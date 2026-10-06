package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.EventoDaEscala;
import br.igreja.escala.escala.solver.FuncaoDaEscala;
import br.igreja.escala.escala.solver.Pessoa;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GravacaoDaEscalaTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final long MIDIA = AcessoDeTeste.MIDIA;

    private final LeituraDoPeriodo leitura = mock(LeituraDoPeriodo.class);
    private final VagaRepository vagas = mock(VagaRepository.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final GravacaoDaEscala gravacao = new GravacaoDaEscala(leitura, vagas, periodos, ministerios, auditoria);

    private final Periodo novembro = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);
    private final Andamento andamento =
            new Andamento(400L, MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA.getId(), Instant.EPOCH);

    private final EventoDaEscala domingo = new EventoDaEscala(
            500L, "Culto de domingo", LocalDateTime.of(2026, 11, 1, 18, 0), LocalDateTime.of(2026, 11, 1, 20, 0));
    private final FuncaoDaEscala projecao = new FuncaoDaEscala(100L, "Projeção", 1, 1);
    private final FuncaoDaEscala transmissao = new FuncaoDaEscala(101L, "Transmissão", 1, 1);
    private final Pessoa ana = new Pessoa(30L, "Ana Souza", Map.of(100L, 201L), Set.of(500L));

    private final Vaga daProjecao = comId(new Vaga(500L, 100L, 1), 1L);
    private final Vaga daTransmissao = comId(new Vaga(500L, 101L, 1), 2L);

    @BeforeEach
    void prepara() {
        ReflectionTestUtils.setField(novembro, "disponibilidadeTravada", true);
        when(periodos.bloquearParaAlterar(400L)).thenReturn(novembro);
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
        when(vagas.findAllById(anyList())).thenReturn(List.of(daProjecao, daTransmissao));
    }

    @Test
    void gravaAPessoaDeCadaVagaEAudita() {
        daTransmissao.escalar(31L);

        int preenchidas = gravacao.gravar(solucao(ana, null), andamento, Duration.ofSeconds(8));

        assertThat(preenchidas).isEqualTo(1);
        assertThat(daProjecao.getUsuarioId()).isEqualTo(30L);
        assertThat(daTransmissao.isVazia())
                .as("o rascunho anterior é substituído")
                .isTrue();
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.GERAR_ESCALA,
                        AcessoDeTeste.GERENTE_DA_MIDIA.getId(),
                        MIDIA,
                        null,
                        "Escala de Novembro 2026 gerada (Mídia): 1 de 2 vagas preenchidas em 8 s."
                                + " Pontuação 0hard/-101medium/-1soft."));
    }

    @Test
    void naoMexeNaVagaPresa() {
        daTransmissao.escalar(31L);
        ReflectionTestUtils.setField(daTransmissao, "fixada", true);

        gravacao.gravar(solucao(ana, null), andamento, Duration.ofSeconds(8));

        assertThat(daTransmissao.getUsuarioId()).isEqualTo(31L);
    }

    @Test
    void disponibilidadeDestravadaNoMeioNaoMudaNada() {
        novembro.destravarDisponibilidade();

        assertThatThrownBy(() -> gravacao.gravar(solucao(ana, null), andamento, Duration.ofSeconds(8)))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("A disponibilidade de novembro foi destravada durante a geração, e a escala não mudou."
                        + " Trave de novo e gere a escala.");
        assertThat(daProjecao.isVazia()).isTrue();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void escalaPublicadaNoMeioNaoMudaNada() {
        novembro.publicarEscala();

        assertThatThrownBy(() -> gravacao.gravar(solucao(ana, null), andamento, Duration.ofSeconds(8)))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessageStartingWith("A escala de novembro está publicada.");
        assertThat(daProjecao.isVazia()).isTrue();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void prepararBloqueiaOPeriodoERecusaAEscalaPublicada() {
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));
        novembro.publicarEscala();

        assertThatThrownBy(() -> gravacao.preparar(MIDIA, NOVEMBRO))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessageStartingWith("A escala de novembro está publicada.");
        verify(periodos).bloquearParaAlterar(400L);
        verify(leitura, never()).ler(any(), any());
    }

    @Test
    void prepararCriaAsQueFaltamApagaAsQueSairamEMontaOProblema() {
        var evento = ExemplosDeEvento.comId(
                Evento.avulso(novembro, "Culto", LocalDate.of(2026, 11, 1), LocalTime.of(18, 0), DUAS_HORAS), 500L);
        var cancelado = ExemplosDeEvento.comId(
                Evento.avulso(novembro, "Ensaio", LocalDate.of(2026, 11, 7), LocalTime.of(18, 0), DUAS_HORAS), 501L);
        cancelado.cancelar();
        var doCancelado = comId(new Vaga(501L, 100L, 1), 3L);
        var midia = Exemplos.midia();
        when(leitura.ler(MIDIA, NOVEMBRO))
                .thenReturn(new DadosDoPeriodo(
                        MIDIA,
                        "Mídia",
                        NOVEMBRO,
                        novembro,
                        List.of(evento, cancelado),
                        List.of(Exemplos.projecao(midia)),
                        List.of(doCancelado),
                        List.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        Map.of(),
                        RegrasDoMinisterio.padrao(),
                        List.of(),
                        LocalDateTime.of(2026, 10, 7, 10, 0)));
        when(vagas.saveAll(anyList())).thenAnswer(chamada -> {
            List<Vaga> novas = chamada.getArgument(0);
            novas.forEach(nova -> comId(nova, 9L));
            return novas;
        });

        var escala = gravacao.preparar(MIDIA, NOVEMBRO);

        verify(vagas).deleteAll(List.of(doCancelado));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Vaga>> novas = ArgumentCaptor.forClass(List.class);
        verify(vagas).saveAll(novas.capture());
        assertThat(novas.getValue())
                .singleElement()
                .extracting(Vaga::getEventoId)
                .isEqualTo(500L);
        assertThat(escala.getVagas())
                .singleElement()
                .extracting(VagaPlanejada::getId)
                .isEqualTo(9L);
    }

    private EscalaDoPeriodo solucao(Pessoa naProjecao, Pessoa naTransmissao) {
        var escala = new EscalaDoPeriodo(
                400L,
                List.of(ana),
                List.of(),
                null,
                List.of(
                        new VagaPlanejada(1L, domingo, projecao, 1, false, naProjecao),
                        new VagaPlanejada(2L, domingo, transmissao, 1, false, naTransmissao)),
                null);
        escala.setPontuacao(HardMediumSoftScore.of(0, -101, -1));
        return escala;
    }

    private static Vaga comId(Vaga vaga, Long id) {
        return ExemplosDeEvento.comId(vaga, id);
    }
}
