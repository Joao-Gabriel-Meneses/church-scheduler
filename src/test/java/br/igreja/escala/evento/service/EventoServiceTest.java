package br.igreja.escala.evento.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static br.igreja.escala.evento.ExemplosDeEvento.cultoDeDomingo;
import static br.igreja.escala.evento.ExemplosDeEvento.cultoDeQuinta;
import static br.igreja.escala.evento.ExemplosDeEvento.periodo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventoServiceTest {

    /** Quarta, 7 de outubro de 2026, 10h em São Paulo. */
    private static final Clock HOJE = Clock.fixed(
            ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO);

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final EventoRepository eventos = mock(EventoRepository.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final ModeloEventoService modelos = mock(ModeloEventoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final EventoService servico = new EventoService(eventos, periodos, modelos, ministerios, HOJE);

    private final Periodo outubro = periodo(1L, OUTUBRO);
    private final Periodo novembro = periodo(1L, NOVEMBRO);

    @BeforeEach
    void prepara() {
        when(periodos.obterOuCriar(1L, OUTUBRO)).thenReturn(outubro);
        when(periodos.obterOuCriar(1L, NOVEMBRO)).thenReturn(novembro);
        when(modelos.ativos(1L)).thenReturn(List.of(cultoDeDomingo(1L), cultoDeQuinta(1L)));
    }

    @Test
    void proximoMesEOSeguinteAoDeHojeEmSaoPaulo() {
        assertThat(servico.proximoMes()).isEqualTo(NOVEMBRO);
    }

    @Test
    void geraOsDomingosEQuintasDoMesInteiro() {
        assertThat(servico.gerarDoMes(1L, NOVEMBRO)).isEqualTo(9);

        assertThat(gravados())
                .extracting(Evento::getData)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 8),
                        LocalDate.of(2026, 11, 15),
                        LocalDate.of(2026, 11, 22),
                        LocalDate.of(2026, 11, 29),
                        LocalDate.of(2026, 11, 5),
                        LocalDate.of(2026, 11, 12),
                        LocalDate.of(2026, 11, 19),
                        LocalDate.of(2026, 11, 26));
    }

    @Test
    void noMesAtualSoGeraDeHojeEmDiante() {
        assertThat(servico.gerarDoMes(1L, OUTUBRO)).isEqualTo(7);

        assertThat(gravados()).extracting(Evento::getData).allMatch(data -> !data.isBefore(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void gerarDeNovoNaoDuplicaNemRecriaCancelado() {
        when(eventos.existsByModeloIdAndData(anyLong(), any())).thenReturn(true);
        when(eventos.existsByModeloIdAndData(eq(300L), eq(LocalDate.of(2026, 11, 29))))
                .thenReturn(false);

        assertThat(servico.gerarDoMes(1L, NOVEMBRO)).isEqualTo(1);
        verify(eventos, times(1)).save(any());
    }

    @Test
    void mesQueJaPassouNaoGera() {
        assertThatThrownBy(() -> servico.gerarDoMes(1L, YearMonth.of(2026, 9)))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Setembro 2026 já passou: não dá para criar eventos nele.");
        verify(eventos, never()).save(any());
    }

    @Test
    void semModelosAtivosNaoCriaNada() {
        when(modelos.ativos(1L)).thenReturn(List.of());

        assertThat(servico.gerarDoMes(1L, NOVEMBRO)).isZero();
    }

    @Test
    void avulsoNoPeriodoDoMesDaData() {
        when(eventos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var conferencia = servico.criarAvulso(
                1L, new DadosDoEvento("Conferência de jovens", LocalDate.of(2026, 11, 14), LocalTime.of(15, 0), 120));

        assertThat(conferencia.isAvulso()).isTrue();
        assertThat(conferencia.getPeriodo()).isSameAs(novembro);
    }

    @Test
    void avulsoNaoPodeSerNoPassado() {
        assertThatThrownBy(() -> servico.criarAvulso(
                        1L, new DadosDoEvento("Ensaio", LocalDate.of(2026, 10, 6), LocalTime.NOON, 120)))
                .isInstanceOfSatisfying(
                        RegraVioladaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("data"));
        verify(eventos, never()).save(any());
    }

    @Test
    void alterarMudaHorarioENomeDoEventoDoModeloMasNaoAData() {
        var evento = Evento.doModelo(cultoDeDomingo(1L), novembro, LocalDate.of(2026, 11, 1));
        when(eventos.findByIdAndMinisterioId(500L, 1L)).thenReturn(Optional.of(evento));

        servico.alterar(
                1L, 500L, new DadosDoEvento("Culto de Ceia", LocalDate.of(2026, 11, 1), LocalTime.of(19, 0), 120));
        assertThat(evento.getHorario()).isEqualTo(LocalTime.of(19, 0));
        assertThat(evento.getNome()).isEqualTo("Culto de Ceia");

        assertThatThrownBy(() -> servico.alterar(
                        1L, 500L, new DadosDoEvento("Culto", LocalDate.of(2026, 11, 2), LocalTime.of(19, 0), 120)))
                .isInstanceOfSatisfying(
                        RegraVioladaException.class,
                        recusa ->
                                assertThat(recusa.getMessage()).startsWith("A data de um evento do modelo não muda."));
        assertThat(evento.getData()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void avulsoMudaDeDataParaOutroMes() {
        var ensaio = Evento.avulso(outubro, "Ensaio", LocalDate.of(2026, 10, 24), LocalTime.of(15, 0), DUAS_HORAS);
        when(eventos.findByIdAndMinisterioId(501L, 1L)).thenReturn(Optional.of(ensaio));

        servico.alterar(1L, 501L, new DadosDoEvento("Ensaio", LocalDate.of(2026, 11, 7), LocalTime.of(15, 0), 120));

        assertThat(ensaio.getPeriodo()).isSameAs(novembro);
        assertThatThrownBy(() -> servico.alterar(
                        1L, 501L, new DadosDoEvento("Ensaio", LocalDate.of(2026, 10, 1), LocalTime.of(15, 0), 120)))
                .isInstanceOf(RegraVioladaException.class);
    }

    @Test
    void cancelaEReativa() {
        var evento = Evento.doModelo(cultoDeDomingo(1L), novembro, LocalDate.of(2026, 11, 1));
        when(eventos.findByIdAndMinisterioId(500L, 1L)).thenReturn(Optional.of(evento));

        assertThat(servico.cancelar(1L, 500L).isCancelado()).isTrue();
        assertThat(servico.reativar(1L, 500L).isCancelado()).isFalse();
    }

    @Test
    void eventoDeOutroMinisterioE404() {
        when(eventos.findByIdAndMinisterioId(500L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(2L, 500L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.cancelar(2L, 500L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.alterar(
                        2L, 500L, new DadosDoEvento("X", LocalDate.of(2026, 11, 1), LocalTime.NOON, 120)))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void doMesListaOsEventosDoPeriodoJaEscritos() {
        when(periodos.doMes(1L, NOVEMBRO)).thenReturn(Optional.of(novembro));
        var evento = Evento.doModelo(cultoDeDomingo(1L), novembro, LocalDate.of(2026, 11, 1));
        evento.cancelar();
        when(eventos.findByPeriodoIdOrderByDataAscHorarioAsc(400L)).thenReturn(List.of(evento));
        when(periodos.doMes(1L, OUTUBRO)).thenReturn(Optional.empty());

        assertThat(servico.doMes(1L, NOVEMBRO))
                .singleElement()
                .extracting(EventoResumo::descricao)
                .isEqualTo("01/11 · Dom · 18h00 às 20h00 · Cancelado");
        assertThat(servico.doMes(1L, OUTUBRO)).isEmpty();
    }

    private List<Evento> gravados() {
        var gravado = ArgumentCaptor.forClass(Evento.class);
        verify(eventos, atLeastOnce()).save(gravado.capture());
        return gravado.getAllValues();
    }
}
