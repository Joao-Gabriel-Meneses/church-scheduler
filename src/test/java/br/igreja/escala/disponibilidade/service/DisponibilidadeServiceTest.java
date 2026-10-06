package br.igreja.escala.disponibilidade.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DisponibilidadeServiceTest {

    /** Quarta, 7 de outubro de 2026, 10h em São Paulo. */
    private static final Clock AGORA = Clock.fixed(
            ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO);

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final long ANA = 30L;
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final DisponibilidadeRepository disponibilidades = mock(DisponibilidadeRepository.class);
    private final EventoService eventos = mock(EventoService.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final MembroService membros = mock(MembroService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DisponibilidadeService servico =
            new DisponibilidadeService(disponibilidades, eventos, periodos, membros, ministerios, auditoria, AGORA);

    private final Periodo novembro = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);
    private final Evento culto = ExemplosDeEvento.comId(
            Evento.doModelo(ExemplosDeEvento.cultoDeDomingo(MIDIA), novembro, LocalDate.of(2026, 11, 1)), 500L);

    @BeforeEach
    void prepara() {
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
        when(membros.buscarQueServe(MIDIA, ANA))
                .thenReturn(new UsuarioResumo(ANA, "Ana Souza", "ana@x.com", null, false, false, true));
        when(eventos.buscar(MIDIA, 500L)).thenReturn(culto);
        when(periodos.bloquearParaAlterar(400L)).thenReturn(novembro);
        when(disponibilidades.findByUsuarioIdAndEventoId(ANA, 500L)).thenReturn(Optional.empty());
    }

    @Test
    void membroMarcaAPropriaRespostaDepoisDeBloquearOPeriodo() {
        assertThat(servico.marcar(ANA, MIDIA, 500L, Resposta.PODE)).isTrue();

        var gravada = ArgumentCaptor.forClass(Disponibilidade.class);
        var ordem = inOrder(periodos, disponibilidades);
        ordem.verify(periodos).bloquearParaAlterar(400L);
        ordem.verify(disponibilidades).save(gravada.capture());
        assertThat(gravada.getValue().getUsuarioId()).isEqualTo(ANA);
        assertThat(gravada.getValue().getEventoId()).isEqualTo(500L);
        assertThat(gravada.getValue().getResposta()).isEqualTo(Resposta.PODE);
        assertThat(gravada.getValue().marcadaPorOutro()).isFalse();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void outraRespostaAtualizaAMesmaLinhaEOToqueDuploNaoMudaNada() {
        var existente = new Disponibilidade(ANA, culto, Resposta.PODE, ANA);
        when(disponibilidades.findByUsuarioIdAndEventoId(ANA, 500L)).thenReturn(Optional.of(existente));

        assertThat(servico.marcar(ANA, MIDIA, 500L, Resposta.PODE))
                .as("toque duplo")
                .isFalse();
        assertThat(servico.marcar(ANA, MIDIA, 500L, Resposta.NAO_PODE)).isTrue();

        assertThat(existente.getResposta()).isEqualTo(Resposta.NAO_PODE);
        verify(disponibilidades, never()).save(any());
    }

    @Test
    void quemNaoServeNoMinisterioOuEventoDeOutroMinisterioE404SemGravar() {
        when(membros.buscarQueServe(AcessoDeTeste.LOUVOR, ANA)).thenThrow(new NaoEncontradoException("Ana no Louvor"));
        when(eventos.buscar(MIDIA, 999L)).thenThrow(new NaoEncontradoException("Evento 999 na Mídia"));

        assertThatThrownBy(() -> servico.marcar(ANA, AcessoDeTeste.LOUVOR, 500L, Resposta.PODE))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.marcar(ANA, MIDIA, 999L, Resposta.PODE))
                .isInstanceOf(NaoEncontradoException.class);

        verify(periodos, never()).bloquearParaAlterar(anyLong());
        verify(disponibilidades, never()).save(any());
    }

    @Test
    void prefiroNaoAindaNaoVale() {
        assertThatThrownBy(() -> servico.marcar(ANA, MIDIA, 500L, Resposta.PREFERE_NAO))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Sua resposta para 01/11 · Culto de domingo não mudou: \"Prefiro não\" ainda não vale."
                        + " Marque Pode ou Não pode.");

        verify(disponibilidades, never()).save(any());
    }

    @Test
    void eventoCanceladoOuQueJaComecouNaoRecebeResposta() {
        var outubro = ExemplosDeEvento.periodo(MIDIA, YearMonth.of(2026, 10));
        var hojeCedo = ExemplosDeEvento.comId(
                Evento.avulso(outubro, "Reunião", LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), DUAS_HORAS), 501L);
        when(eventos.buscar(MIDIA, 501L)).thenReturn(hojeCedo);
        culto.cancelar();

        assertThatThrownBy(() -> servico.marcar(ANA, MIDIA, 501L, Resposta.PODE))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("07/10 · Reunião já começou: não dá mais para marcar.");
        assertThatThrownBy(() -> servico.marcar(ANA, MIDIA, 500L, Resposta.PODE))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("01/11 · Culto de domingo foi cancelado: não dá mais para marcar.");

        verify(disponibilidades, never()).save(any());
    }

    @Test
    void comOPeriodoTravadoORespostaNaoMuda() {
        novembro.travarDisponibilidade();
        var existente = new Disponibilidade(ANA, culto, Resposta.PODE, ANA);
        when(disponibilidades.findByUsuarioIdAndEventoId(ANA, 500L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servico.marcar(ANA, MIDIA, 500L, Resposta.NAO_PODE))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Sua resposta para 01/11 · Culto de domingo não mudou: o gerente travou a disponibilidade"
                        + " de novembro.");

        assertThat(existente.getResposta()).isEqualTo(Resposta.PODE);
        verify(disponibilidades, never()).save(any());
    }

    @Test
    void gerenteMarcaEmNomeDaPessoaComOPeriodoTravadoERegistraNaAuditoria() {
        novembro.travarDisponibilidade();

        assertThat(servico.marcarPeloGerente(MIDIA, ANA, 500L, Resposta.PODE, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        var gravada = ArgumentCaptor.forClass(Disponibilidade.class);
        verify(disponibilidades).save(gravada.capture());
        assertThat(gravada.getValue().getUsuarioId()).isEqualTo(ANA);
        assertThat(gravada.getValue().getMarcadoPorId()).isEqualTo(AcessoDeTeste.GERENTE_DA_MIDIA.getId());
        verify(periodos).bloquearParaAlterar(400L);
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.MARCAR_DISPONIBILIDADE,
                        AcessoDeTeste.GERENTE_DA_MIDIA.getId(),
                        MIDIA,
                        ANA,
                        "Pode para Ana Souza em 01/11 · Culto de domingo, com a disponibilidade travada (Mídia)."));
    }

    @Test
    void gerenteConfirmandoAMesmaRespostaNaoRegistraNada() {
        var existente = new Disponibilidade(ANA, culto, Resposta.NAO_PODE, ANA);
        when(disponibilidades.findByUsuarioIdAndEventoId(ANA, 500L)).thenReturn(Optional.of(existente));

        assertThat(servico.marcarPeloGerente(MIDIA, ANA, 500L, Resposta.NAO_PODE, AcessoDeTeste.ADMIN))
                .isFalse();
        assertThat(servico.marcarPeloGerente(MIDIA, ANA, 500L, Resposta.PODE, AcessoDeTeste.ADMIN))
                .isTrue();

        assertThat(existente.marcadaPorOutro()).isTrue();
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.MARCAR_DISPONIBILIDADE,
                        AcessoDeTeste.ADMIN.getId(),
                        MIDIA,
                        ANA,
                        "Pode para Ana Souza em 01/11 · Culto de domingo (Mídia)."));
    }

    @Test
    void gerenteNaoMarcaPorQuemNaoServeNemPrefiroNao() {
        when(membros.buscarQueServe(MIDIA, 99L)).thenThrow(new NaoEncontradoException("99 na Mídia"));
        when(eventos.buscar(MIDIA, 999L)).thenThrow(new NaoEncontradoException("Evento 999 na Mídia"));

        assertThatThrownBy(() ->
                        servico.marcarPeloGerente(MIDIA, 99L, 500L, Resposta.PODE, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() ->
                        servico.marcarPeloGerente(MIDIA, ANA, 999L, Resposta.PODE, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.marcarPeloGerente(
                        MIDIA, ANA, 500L, Resposta.PREFERE_NAO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class);

        verify(disponibilidades, never()).save(any());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void travarRegistraNaAuditoria() {
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(true);

        assertThat(servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.TRAVAR_DISPONIBILIDADE,
                        AcessoDeTeste.GERENTE_DA_MIDIA.getId(),
                        MIDIA,
                        null,
                        "Disponibilidade de Novembro 2026 travada (Mídia)."));
    }

    @Test
    void destravarRegistraNaAuditoria() {
        when(periodos.destravarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(true);

        assertThat(servico.destravar(MIDIA, NOVEMBRO, false, AcessoDeTeste.ADMIN))
                .isTrue();

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.DESTRAVAR_DISPONIBILIDADE,
                        AcessoDeTeste.ADMIN.getId(),
                        MIDIA,
                        null,
                        "Disponibilidade de Novembro 2026 destravada (Mídia)."));
    }

    @Test
    void comAEscalaPublicadaSoDestravaComConfirmacao() {
        var publicada = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);
        publicada.publicarEscala();
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(publicada));
        when(periodos.destravarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(true);

        assertThatThrownBy(() -> servico.destravar(MIDIA, NOVEMBRO, false, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("A escala de novembro está publicada e foi gerada com estas respostas. Confirme para"
                        + " destravar a disponibilidade.");
        verify(periodos, never()).destravarDisponibilidade(MIDIA, NOVEMBRO);

        assertThat(servico.destravar(MIDIA, NOVEMBRO, true, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();
    }

    @Test
    void travarOuDestravarDeNovoNaoRegistraOutraVez() {
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(false);
        when(periodos.destravarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(false);

        assertThat(servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isFalse();
        assertThat(servico.destravar(MIDIA, NOVEMBRO, false, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isFalse();

        verify(auditoria, never()).registrar(any());
    }

    @Test
    void ministerioQueNaoExisteE404EMesSemEventosERecusado() {
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO))
                .thenThrow(RegraVioladaException.geral("Novembro 2026 ainda não tem eventos."));

        assertThatThrownBy(() -> servico.travar(9L, NOVEMBRO, AcessoDeTeste.ADMIN))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN))
                .isInstanceOf(RegraVioladaException.class);
        verify(auditoria, never()).registrar(any());
    }
}
