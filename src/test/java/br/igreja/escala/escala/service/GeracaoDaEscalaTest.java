package br.igreja.escala.escala.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverJobBuilder;
import ai.timefold.solver.core.api.solver.SolverManager;
import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GeracaoDaEscalaTest {

    private static final Clock AGORA = Clock.fixed(
            ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO);
    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    @SuppressWarnings("unchecked")
    private final SolverManager<EscalaDoPeriodo> solverManager = mock(SolverManager.class);

    @SuppressWarnings("unchecked")
    private final SolverJobBuilder<EscalaDoPeriodo> construtor = mock(SolverJobBuilder.class, RETURNS_SELF);

    @SuppressWarnings("unchecked")
    private final SolverJob<EscalaDoPeriodo> trabalho = mock(SolverJob.class);

    private final GravacaoDaEscala gravacao = mock(GravacaoDaEscala.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final EventoService eventos = mock(EventoService.class);

    /** A fila das gerações: guarda o que chegou e só roda quando o teste manda. */
    private final List<Runnable> fila = new ArrayList<>();

    private final GeracaoDaEscala servico = new GeracaoDaEscala(
            solverManager, gravacao, new GeracoesEmAndamento(), periodos, eventos, AGORA, fila::add);

    private final Periodo novembro = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);
    private final EscalaDoPeriodo problema = new EscalaDoPeriodo(400L, List.of(), List.of(), null, List.of(), null);
    private final EscalaDoPeriodo solucao = new EscalaDoPeriodo(400L, List.of(), List.of(), null, List.of(), null);

    @BeforeEach
    void prepara() throws Exception {
        ReflectionTestUtils.setField(novembro, "disponibilidadeTravada", true);
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO))
                .thenReturn(List.of(Evento.avulso(
                        novembro, "Culto", LocalDate.of(2026, 11, 1), LocalTime.of(18, 0), Duration.ofHours(2))));
        when(solverManager.solveBuilder()).thenReturn(construtor);
        when(construtor.run()).thenReturn(trabalho);
        when(trabalho.getFinalBestSolution()).thenReturn(solucao);
        when(gravacao.preparar(MIDIA, NOVEMBRO)).thenReturn(problema);
    }

    @Test
    void semTravaNaoGera() {
        novembro.destravarDisponibilidade();

        assertThatThrownBy(() -> servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Trave a disponibilidade de novembro antes de gerar a escala: a geração usa as respostas"
                        + " travadas.");
        assertThat(fila).isEmpty();
    }

    @Test
    void mesSemEventosNaoGera() {
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .hasMessage("Novembro 2026 ainda não tem eventos: não há escala para gerar.");

        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO)).thenReturn(List.of());
        assertThatThrownBy(() -> servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .hasMessage("Não há eventos por vir em novembro: não há escala para gerar.");
        assertThat(fila).isEmpty();
    }

    @Test
    void segundoCliqueNaoDisparaOutraGeracao() {
        var primeira = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        var segunda = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN);

        assertThat(segunda).isSameAs(primeira);
        assertThat(primeira.isGerando()).isTrue();
        assertThat(primeira.getAutorId()).isEqualTo(AcessoDeTeste.GERENTE_DA_MIDIA.getId());
        assertThat(fila).hasSize(1);
        assertThat(servico.andamento(MIDIA, NOVEMBRO)).contains(primeira);
    }

    @Test
    void naVezDelaLeResolveEGravaEmSequencia() throws Exception {
        var andamento = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        when(gravacao.gravar(any(), any(), any())).thenReturn(0);

        fila.getFirst().run();

        var ordem = inOrder(gravacao, construtor, trabalho);
        ordem.verify(gravacao).preparar(MIDIA, NOVEMBRO);
        ordem.verify(construtor).withProblem(problema);
        ordem.verify(trabalho).getFinalBestSolution();
        ordem.verify(gravacao).gravar(solucao, andamento, Duration.ZERO);
        verify(construtor).withProblemId(400L);
        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.CONCLUIDA);
        assertThat(andamento.getMensagem()).isEqualTo("Escala de novembro gerada: 0 de 0 vagas preenchidas");
        assertThat(andamento.getFim()).isCompleted();
        assertThat(servico.retirarTerminada(MIDIA, NOVEMBRO)).contains(andamento);
        assertThat(servico.andamento(MIDIA, NOVEMBRO)).isEmpty();
    }

    @Test
    void destravadaNoMeioFalhaComOMotivoEOProximoCliqueGeraDeNovo() {
        var andamento = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        when(gravacao.gravar(any(), any(), any())).thenThrow(RegraVioladaException.geral("Foi destravada."));

        fila.getFirst().run();

        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.FALHOU);
        assertThat(andamento.getMensagem()).isEqualTo("Foi destravada.");
        assertThat(servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isNotSameAs(andamento);
        assertThat(fila).hasSize(2);
    }

    @Test
    void erroNoSolverViraFalhaSemDetalhesParaOGerente() throws Exception {
        var andamento = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        when(trabalho.getFinalBestSolution())
                .thenThrow(new ExecutionException(new IllegalStateException("banco caiu")));

        fila.getFirst().run();

        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.FALHOU);
        assertThat(andamento.getMensagem())
                .isEqualTo("A geração da escala falhou. Tente de novo; se continuar, avise o admin.");
        verify(gravacao, never()).gravar(any(), any(), any());
    }

    @Test
    void filaCheiaNaoDeixaAGeracaoPresa() {
        var recusa = new GeracaoDaEscala(
                solverManager, gravacao, new GeracoesEmAndamento(), periodos, eventos, AGORA, tarefa -> {
                    throw new RejectedExecutionException("desligando");
                });

        assertThatThrownBy(() -> recusa.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(recusa.andamento(MIDIA, NOVEMBRO)).isEmpty();
    }
}
