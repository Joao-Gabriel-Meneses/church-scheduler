package br.igreja.escala.escala.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.timefold.solver.core.api.solver.SolverJobBuilder;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.event.FinalBestSolutionEvent;
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
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    private final GravacaoDaEscala gravacao = mock(GravacaoDaEscala.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final EventoService eventos = mock(EventoService.class);
    private final GeracaoDaEscala servico =
            new GeracaoDaEscala(solverManager, gravacao, new GeracoesEmAndamento(), periodos, eventos, AGORA);

    private final Periodo novembro = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);

    @BeforeEach
    void prepara() {
        ReflectionTestUtils.setField(novembro, "disponibilidadeTravada", true);
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO))
                .thenReturn(List.of(Evento.avulso(
                        novembro, "Culto", LocalDate.of(2026, 11, 1), LocalTime.of(18, 0), Duration.ofHours(2))));
        when(solverManager.solveBuilder()).thenReturn(construtor);
    }

    @Test
    void semTravaNaoGera() {
        novembro.destravarDisponibilidade();

        assertThatThrownBy(() -> servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Trave a disponibilidade de novembro antes de gerar a escala: a geração usa as respostas"
                        + " travadas.");
        verify(solverManager, never()).solveBuilder();
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
        verify(solverManager, never()).solveBuilder();
    }

    @Test
    void segundoCliqueNaoDisparaOutraGeracao() {
        var primeira = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        var segunda = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN);

        assertThat(segunda).isSameAs(primeira);
        assertThat(primeira.isGerando()).isTrue();
        assertThat(primeira.getAutorId()).isEqualTo(AcessoDeTeste.GERENTE_DA_MIDIA.getId());
        verify(solverManager, times(1)).solveBuilder();
        verify(construtor).withProblemId(400L);
        verify(construtor, times(1)).run();
        assertThat(servico.andamento(MIDIA, NOVEMBRO)).contains(primeira);
    }

    @Test
    void aoTerminarGravaOResultadoEDizQuantasVagasPreencheu() {
        var andamento = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        var solucao = new EscalaDoPeriodo(400L, List.of(), List.of(), null, List.of(), null);
        when(gravacao.gravar(any(), any(), any())).thenReturn(0);

        terminar(solucao);

        verify(gravacao).gravar(solucao, andamento, Duration.ZERO);
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

        terminar(new EscalaDoPeriodo(400L, List.of(), List.of(), null, List.of(), null));

        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.FALHOU);
        assertThat(andamento.getMensagem()).isEqualTo("Foi destravada.");
        assertThat(servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isNotSameAs(andamento);
        verify(solverManager, times(2)).solveBuilder();
    }

    @Test
    void erroNoSolverViraFalhaSemDetalhesParaOGerente() {
        var andamento = servico.iniciar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BiConsumer<Object, Throwable>> tratador = ArgumentCaptor.forClass(BiConsumer.class);
        verify(construtor).withExceptionHandler(tratador.capture());

        tratador.getValue().accept(400L, new IllegalStateException("banco caiu"));

        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.FALHOU);
        assertThat(andamento.getMensagem())
                .isEqualTo("A geração da escala falhou. Tente de novo; se continuar, avise o admin.");
    }

    @SuppressWarnings("unchecked")
    private void terminar(EscalaDoPeriodo solucao) {
        ArgumentCaptor<Consumer<FinalBestSolutionEvent<EscalaDoPeriodo>>> fim = ArgumentCaptor.forClass(Consumer.class);
        verify(construtor).withFinalBestSolutionEventConsumer(fim.capture());
        fim.getValue().accept(() -> solucao);
    }
}
