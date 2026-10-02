package br.igreja.escala.escala.service;

import ai.timefold.solver.core.api.solver.SolverManager;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gera a escala de um mês em segundo plano, com o SolverManager do Timefold (até 30 s, uma geração por vez no app). O
 * resultado vira o rascunho; as vagas fixadas e forçadas ficam como estão. Só gera com a disponibilidade travada.
 */
@Service
public class GeracaoDaEscala {

    private static final Logger log = LoggerFactory.getLogger(GeracaoDaEscala.class);

    private final SolverManager<EscalaDoPeriodo> solverManager;
    private final GravacaoDaEscala gravacao;
    private final GeracoesEmAndamento andamentos;
    private final PeriodoService periodos;
    private final EventoService eventos;
    private final Clock relogio;

    GeracaoDaEscala(
            SolverManager<EscalaDoPeriodo> solverManager,
            GravacaoDaEscala gravacao,
            GeracoesEmAndamento andamentos,
            PeriodoService periodos,
            EventoService eventos,
            Clock relogio) {
        this.solverManager = solverManager;
        this.gravacao = gravacao;
        this.andamentos = andamentos;
        this.periodos = periodos;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Começa a geração, ou devolve a que já está rodando no período (o segundo clique não dispara outra).
     *
     * @throws RegraVioladaException se o mês não tem eventos por vir ou se a disponibilidade não está travada
     */
    public Andamento iniciar(Long ministerioId, YearMonth mes, UsuarioAutenticado autor) {
        var periodo = exigirPronto(ministerioId, mes);
        var nova = new Andamento(periodo.getId(), ministerioId, mes, autor.getId(), Instant.now(relogio));
        var registrada = andamentos.registrar(nova);
        if (registrada != nova) {
            return registrada;
        }
        try {
            solverManager
                    .solveBuilder()
                    .withProblemId(periodo.getId())
                    .withProblemFinder(id -> gravacao.preparar(ministerioId, mes))
                    .withBestSolutionEventConsumer(evento -> nova.melhorAte(
                            preenchidas(evento.solution()),
                            evento.solution().getVagas().size()))
                    .withFinalBestSolutionEventConsumer(evento -> concluir(nova, evento.solution()))
                    .withExceptionHandler((id, erro) -> falhou(nova, erro))
                    .run();
        } catch (RuntimeException erro) {
            andamentos.remover(nova);
            throw erro;
        }
        return nova;
    }

    /** A geração do mês que está rodando, ou a que terminou e ainda não foi avisada. */
    public Optional<Andamento> andamento(Long ministerioId, YearMonth mes) {
        return periodos.doMes(ministerioId, mes).flatMap(periodo -> andamentos.doPeriodo(periodo.getId()));
    }

    /** Tira o resultado da geração do mês que terminou, para avisar o gerente uma vez. */
    public Optional<Andamento> retirarTerminada(Long ministerioId, YearMonth mes) {
        return periodos.doMes(ministerioId, mes).flatMap(periodo -> andamentos.retirarTerminada(periodo.getId()));
    }

    private Periodo exigirPronto(Long ministerioId, YearMonth mes) {
        var nomeDoMes = Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
        var periodo = periodos.doMes(ministerioId, mes)
                .orElseThrow(() -> RegraVioladaException.geral(
                        Datas.mesPorExtenso(mes) + " ainda não tem eventos: não há escala para gerar."));
        if (!periodo.isDisponibilidadeTravada()) {
            throw RegraVioladaException.geral("Trave a disponibilidade de " + nomeDoMes
                    + " antes de gerar a escala: a geração usa as respostas travadas.");
        }
        if (eventos.porVirDoMes(ministerioId, mes).isEmpty()) {
            throw RegraVioladaException.geral("Não há eventos por vir em " + nomeDoMes + ": não há escala para gerar.");
        }
        return periodo;
    }

    private void concluir(Andamento andamento, EscalaDoPeriodo solucao) {
        var duracao = Duration.between(andamento.getInicio(), Instant.now(relogio));
        try {
            int preenchidas = gravacao.gravar(solucao, andamento, duracao);
            int total = solucao.getVagas().size();
            log.info(
                    "Escala do período {} gerada em {} ms: {} de {} vagas preenchidas, pontuação {}",
                    andamento.getPeriodoId(),
                    duracao.toMillis(),
                    preenchidas,
                    total,
                    solucao.getPontuacao());
            andamento.concluir(
                    preenchidas,
                    total,
                    duracao,
                    "Escala de " + Datas.nomeDoMes(andamento.getMes()).toLowerCase(Locale.ROOT) + " gerada: "
                            + preenchidas + " de " + total + (total == 1 ? " vaga preenchida" : " vagas preenchidas"));
        } catch (RegraVioladaException recusa) {
            andamento.falhar(recusa.getMessage());
        } catch (RuntimeException erro) {
            falhou(andamento, erro);
        }
    }

    private void falhou(Andamento andamento, Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof RegraVioladaException recusa) {
                andamento.falhar(recusa.getMessage());
                return;
            }
        }
        log.error("A geração da escala do período {} falhou", andamento.getPeriodoId(), erro);
        andamento.falhar("A geração da escala falhou. Tente de novo; se continuar, avise o admin.");
    }

    private static int preenchidas(EscalaDoPeriodo escala) {
        return (int) escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() != null)
                .count();
    }
}
