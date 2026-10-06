package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.Pessoas;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Hoje é 07/10/2026, 10h. A Ana (membro, id 20) está na Projeção de eventos de outubro, com a escala publicada. */
class DesistenciaDaEscalaTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final UsuarioAutenticado ANA = Pessoas.membro(20L, "Ana Souza");
    private static final UsuarioAutenticado BRUNO = Pessoas.membro(21L, "Bruno Lima");
    private static final ZonedDateTime AGORA = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO);

    private final VagaRepository vagas = mock(VagaRepository.class);
    private final EventoService eventos = mock(EventoService.class);
    private final FuncaoService funcoes = mock(FuncaoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DesistenciaDaEscala servico = new DesistenciaDaEscala(
            vagas,
            eventos,
            funcoes,
            ministerios,
            periodos,
            auditoria,
            mock(EntityManager.class),
            Clock.fixed(AGORA.toInstant(), Fuso.SAO_PAULO));

    private final Periodo outubro = ExemplosDeEvento.periodo(MIDIA, YearMonth.of(2026, 10));
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());

    @BeforeEach
    void prepara() {
        outubro.publicarEscala();
        when(periodos.bloquearParaAlterar(outubro.getId())).thenReturn(outubro);
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
        when(funcoes.porIds(anyCollection())).thenReturn(List.of(projecao));
    }

    @Test
    void desisteDaPropriaVagaQueFicaVaziaFixadaEAuditada() {
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0)));

        String aviso = servico.desistir(1L, ANA);

        assertThat(aviso).isEqualTo("Você desistiu de Projeção, 11/10 · Dom. O gerente vê o aviso na escala");
        assertThat(vaga.isVazia()).isTrue();
        assertThat(vaga.isFixada()).isTrue();
        assertThat(vaga.getDesistenteId()).isEqualTo(ANA.getId());
        assertThat(vaga.getDesistiuEm()).isEqualTo(AGORA.toInstant());
        var registro = ArgumentCaptor.forClass(RegistroDeAuditoria.class);
        verify(auditoria).registrar(registro.capture());
        assertThat(registro.getValue().acao()).isEqualTo(AcaoAuditada.DESISTIR_DA_VAGA);
        assertThat(registro.getValue().autorId()).isEqualTo(ANA.getId());
        assertThat(registro.getValue().alvoUsuarioId()).isEqualTo(ANA.getId());
        assertThat(registro.getValue().ministerioId()).isEqualTo(MIDIA);
        assertThat(registro.getValue().descricao())
                .isEqualTo("Ana Souza desistiu de Projeção, 11/10 · Dom · 18h00 · Culto (Mídia, escala publicada)."
                        + " A vaga ficou vazia.");
    }

    @Test
    void desistirDuasVezesNaoMudaNadaNaSegunda() {
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0)));
        servico.desistir(1L, ANA);
        var versaoDepoisDaPrimeira = vaga.getDesistiuEm();

        String aviso = servico.desistir(1L, ANA);

        assertThat(aviso).isEqualTo("Você já tinha desistido de Projeção, 11/10 · Dom");
        assertThat(vaga.getDesistiuEm()).isEqualTo(versaoDepoisDaPrimeira);
        verify(auditoria, times(1)).registrar(any());
    }

    @Test
    void vagaDeOutraPessoaE404InclusiveDepoisDeElaDesistir() {
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0)));

        assertThatThrownBy(() -> servico.desistir(1L, BRUNO)).isInstanceOf(NaoEncontradoException.class);
        assertThat(vaga.getUsuarioId()).isEqualTo(ANA.getId());

        servico.desistir(1L, ANA);
        assertThatThrownBy(() -> servico.desistir(1L, BRUNO)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.desistir(99L, ANA)).isInstanceOf(NaoEncontradoException.class);
        verify(auditoria, times(1)).registrar(any());
    }

    @Test
    void quemEntrouNoLugarTiraAVagaDeQuemDesistiu() {
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0)));
        servico.desistir(1L, ANA);
        vaga.ajustar(BRUNO.getId());

        assertThatThrownBy(() -> servico.desistir(1L, ANA)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void escalaEmRascunhoE404() {
        outubro.reabrirEscala();
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0)));

        assertThatThrownBy(() -> servico.desistir(1L, ANA)).isInstanceOf(NaoEncontradoException.class);
        assertThat(vaga.getUsuarioId()).isEqualTo(ANA.getId());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void eventoCanceladoOuFuncaoQueNaoEExigidaE404() {
        var cancelado = evento(500L, LocalDate.of(2026, 10, 11), LocalTime.of(18, 0));
        cancelado.cancelar();
        vagaDaAna(1L, cancelado);

        assertThatThrownBy(() -> servico.desistir(1L, ANA)).isInstanceOf(NaoEncontradoException.class);

        var soTransmissao = evento(501L, LocalDate.of(2026, 10, 18), LocalTime.of(18, 0));
        soTransmissao.exigirFuncoes(Set.of(transmissao.getId()));
        vagaDaAna(2L, soTransmissao);

        assertThatThrownBy(() -> servico.desistir(2L, ANA)).isInstanceOf(NaoEncontradoException.class);
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void eventoQueJaComecouERecusado() {
        var vaga = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 7), LocalTime.of(9, 0)));

        assertThatThrownBy(() -> servico.desistir(1L, ANA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Culto (07/10 · Qua · 09h00) já começou. Para sair da escala, fale com o gerente da"
                        + " Mídia.");
        assertThat(vaga.getUsuarioId()).isEqualTo(ANA.getId());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void prazoVaiAteExatamente24HorasAntes() {
        var noLimite = vagaDaAna(1L, evento(500L, LocalDate.of(2026, 10, 8), LocalTime.of(10, 0)));
        var umMinutoDepois = vagaDaAna(2L, evento(501L, LocalDate.of(2026, 10, 8), LocalTime.of(9, 59)));

        assertThatThrownBy(() -> servico.desistir(2L, ANA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Faltam menos de 24 h para Culto (08/10 · Qui · 09h59). Para desistir agora, fale com o"
                        + " gerente da Mídia.");
        assertThat(umMinutoDepois.getUsuarioId()).isEqualTo(ANA.getId());

        servico.desistir(1L, ANA);
        assertThat(noLimite.isVazia()).isTrue();
    }

    @Test
    void noPrazoComparaComOInicio() {
        var inicio = LocalDateTime.of(2026, 10, 8, 10, 0);

        assertThat(DesistenciaDaEscala.noPrazo(inicio, inicio.minusHours(24))).isTrue();
        assertThat(DesistenciaDaEscala.noPrazo(inicio, inicio.minusHours(25))).isTrue();
        assertThat(DesistenciaDaEscala.noPrazo(inicio, inicio.minusHours(24).plusSeconds(1)))
                .isFalse();
        assertThat(DesistenciaDaEscala.noPrazo(inicio, inicio.plusMinutes(1))).isFalse();
    }

    private Evento evento(Long id, LocalDate data, LocalTime horario) {
        var evento = ExemplosDeEvento.comId(Evento.avulso(outubro, "Culto", data, horario, DUAS_HORAS), id);
        when(eventos.porIds(List.of(id))).thenReturn(List.of(evento));
        return evento;
    }

    private Vaga vagaDaAna(Long id, Evento evento) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), projecao.getId(), 1), id);
        vaga.escalar(ANA.getId());
        when(vagas.findById(id)).thenReturn(Optional.of(vaga));
        return vaga;
    }
}
