package br.igreja.escala.evento.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static br.igreja.escala.evento.ExemplosDeEvento.cultoDeDomingo;
import static br.igreja.escala.evento.ExemplosDeEvento.cultoDeQuinta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.repository.ModeloEventoRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModeloEventoServiceTest {

    private final ModeloEventoRepository modelos = mock(ModeloEventoRepository.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final ModeloEventoService servico = new ModeloEventoService(modelos, ministerios);

    @Test
    void listaDoDomingoAoSabadoComDiaEHorarioEscritos() {
        var sabado = new ModeloEvento(1L, "Ensaio", DayOfWeek.SATURDAY, LocalTime.of(15, 0), DUAS_HORAS);
        sabado.alterar("Ensaio", DayOfWeek.SATURDAY, LocalTime.of(15, 0), DUAS_HORAS, false);
        when(modelos.findByMinisterioIdOrderByDiaDaSemanaAscHorarioAsc(1L))
                .thenReturn(List.of(cultoDeQuinta(1L), sabado, cultoDeDomingo(1L)));

        assertThat(servico.resumos(1L))
                .extracting(ModeloResumo::descricao)
                .containsExactly(
                        "Domingo · 18h00 às 20h00", "Quinta · 19h30 às 21h30", "Sábado · 15h00 às 17h00 · Inativo");
    }

    @Test
    void criaNoMinisterioDaRotaJaComOAtivoDoFormulario() {
        when(modelos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var criado = servico.criar(
                1L, new DadosDoModelo("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 120, false));

        verify(ministerios).buscar(1L);
        assertThat(criado.getMinisterioId()).isEqualTo(1L);
        assertThat(criado.isAtivo()).isFalse();
    }

    @Test
    void alteraOModeloDoMinisterio() {
        var domingo = cultoDeDomingo(1L);
        when(modelos.findByIdAndMinisterioId(300L, 1L)).thenReturn(Optional.of(domingo));

        servico.alterar(
                1L, 300L, new DadosDoModelo("Culto da noite", DayOfWeek.SUNDAY, LocalTime.of(19, 0), 120, true));

        assertThat(domingo.getNome()).isEqualTo("Culto da noite");
        assertThat(domingo.getHorario()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void naoCriaDoisModelosNoMesmoDiaEHorario() {
        var domingo = cultoDeDomingo(1L);
        when(modelos.findByMinisterioIdAndDiaDaSemanaAndHorario(1L, DayOfWeek.SUNDAY, LocalTime.of(18, 0)))
                .thenReturn(Optional.of(domingo));

        assertThatThrownBy(() -> servico.criar(
                        1L, new DadosDoModelo("Culto da noite", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 90, true)))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("horario");
                    assertThat(recusa.getMessage()).isEqualTo("O modelo Culto de domingo já é neste dia e horário.");
                });
        verify(modelos, never()).save(any());
    }

    @Test
    void outroHorarioNoMesmoDiaPode() {
        when(modelos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var manha =
                servico.criar(1L, new DadosDoModelo("Culto da manhã", DayOfWeek.SUNDAY, LocalTime.of(9, 30), 90, true));

        assertThat(manha.getHorario()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void modeloInativoNoMesmoHorarioSugereReativar() {
        var inativo = cultoDeDomingo(1L);
        inativo.alterar("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS, false);
        when(modelos.findByMinisterioIdAndDiaDaSemanaAndHorario(1L, DayOfWeek.SUNDAY, LocalTime.of(18, 0)))
                .thenReturn(Optional.of(inativo));

        assertThatThrownBy(() ->
                        servico.criar(1L, new DadosDoModelo("Culto", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 120, true)))
                .hasMessage("O modelo Culto de domingo já é neste dia e horário. Ele está inativo: reative-o em vez de"
                        + " criar outro.");
    }

    @Test
    void alterarOProprioModeloSemMudarODiaEOHorarioPode() {
        var domingo = cultoDeDomingo(1L);
        when(modelos.findByIdAndMinisterioId(300L, 1L)).thenReturn(Optional.of(domingo));
        when(modelos.findByMinisterioIdAndDiaDaSemanaAndHorario(1L, DayOfWeek.SUNDAY, LocalTime.of(18, 0)))
                .thenReturn(Optional.of(domingo));

        servico.alterar(
                1L, 300L, new DadosDoModelo("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 90, true));

        assertThat(domingo.getDuracao()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void modeloDeOutroMinisterioE404() {
        when(modelos.findByIdAndMinisterioId(300L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(2L, 300L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() ->
                        servico.alterar(2L, 300L, new DadosDoModelo("X", DayOfWeek.SUNDAY, LocalTime.NOON, 120, true)))
                .isInstanceOf(NaoEncontradoException.class);
        verify(modelos, never()).save(any());
    }

    @Test
    void ativosSaoOsQueGeramEventos() {
        when(modelos.findByMinisterioIdAndAtivoTrue(1L)).thenReturn(List.of(cultoDeDomingo(1L)));

        assertThat(servico.ativos(1L)).extracting(ModeloEvento::getNome).containsExactly("Culto de domingo");
    }
}
