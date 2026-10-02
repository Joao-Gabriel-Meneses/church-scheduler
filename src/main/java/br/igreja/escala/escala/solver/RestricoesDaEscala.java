package br.igreja.escala.escala.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.stream.bi.BiConstraintStream;
import br.igreja.escala.escala.domain.TipoDeRegra;

/**
 * Uma restrição por tipo do catálogo, com o nome do tipo. O peso padrão aqui é o da rigidez padrão; o do ministério
 * vem da solução (PesosDasRegras). Toda restrição tem teste no ConstraintVerifier (RestricoesDaEscalaTest).
 */
public class RestricoesDaEscala implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory fabrica) {
        return new Constraint[] {
            pessoasPorFuncao(fabrica),
            habilitacao(fabrica),
            disponibilidade(fabrica),
            umaFuncaoPorEvento(fabrica),
            semSobreposicao(fabrica),
            limitePorPeriodo(fabrica),
            maxPorNivelNoEvento(fabrica),
            prioridadePorData(fabrica),
            equilibrioDeCarga(fabrica)
        };
    }

    /** Mais pessoas numa função do evento que o máximo dela (só acontece com vagas fixadas ou forçadas). */
    Constraint pessoasPorFuncao(ConstraintFactory fabrica) {
        return fabrica.forEach(VagaPlanejada.class)
                .groupBy(VagaPlanejada::getEvento, VagaPlanejada::getFuncao, ConstraintCollectors.count())
                .filter((evento, funcao, pessoas) -> pessoas > funcao.qtdMax())
                .penalize(HardMediumSoftScore.ONE_HARD, (evento, funcao, pessoas) -> pessoas - funcao.qtdMax())
                .asConstraint(TipoDeRegra.PESSOAS_POR_FUNCAO.name());
    }

    Constraint habilitacao(ConstraintFactory fabrica) {
        return fabrica.forEach(VagaPlanejada.class)
                .filter(vaga -> !vaga.getPessoa().habilitadaEm(vaga.getFuncao().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TipoDeRegra.HABILITACAO.name());
    }

    /** Só quem marcou Pode; sem resposta conta como não pode. */
    Constraint disponibilidade(ConstraintFactory fabrica) {
        return fabrica.forEach(VagaPlanejada.class)
                .filter(vaga -> !vaga.getPessoa().pode(vaga.getEvento().id()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TipoDeRegra.DISPONIBILIDADE.name());
    }

    Constraint umaFuncaoPorEvento(ConstraintFactory fabrica) {
        return fabrica.forEachUniquePair(
                        VagaPlanejada.class,
                        Joiners.equal(VagaPlanejada::getEvento),
                        Joiners.equal(VagaPlanejada::getPessoa))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TipoDeRegra.UMA_FUNCAO_POR_EVENTO.name());
    }

    /**
     * A mesma pessoa em eventos que se sobrepõem, aqui ou num compromisso fixo (outro ministério, ou evento já começado).
     * O intervalo é semiaberto: um evento que termina às 11h00 não se sobrepõe ao que começa às 11h00.
     */
    Constraint semSobreposicao(ConstraintFactory fabrica) {
        var entreVagas = fabrica.forEachUniquePair(
                        VagaPlanejada.class,
                        Joiners.equal(VagaPlanejada::getPessoa),
                        Joiners.overlapping(VagaPlanejada::getInicio, VagaPlanejada::getFim))
                .filter((uma, outra) -> !uma.getEvento().equals(outra.getEvento()))
                .map((uma, outra) -> uma);
        var comCompromissos = fabrica.forEach(VagaPlanejada.class)
                .join(
                        CompromissoFixo.class,
                        Joiners.equal(vaga -> vaga.getPessoa().id(), CompromissoFixo::pessoaId),
                        Joiners.overlapping(
                                VagaPlanejada::getInicio,
                                VagaPlanejada::getFim,
                                CompromissoFixo::inicio,
                                CompromissoFixo::fim))
                .filter((vaga, compromisso) ->
                        !compromisso.eventoId().equals(vaga.getEvento().id()))
                .map((vaga, compromisso) -> vaga);
        return entreVagas
                .concat(comCompromissos)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(TipoDeRegra.SEM_SOBREPOSICAO.name());
    }

    /** Eventos do mês por pessoa acima do limite; conta eventos, então dois cultos no mesmo dia contam dois. */
    Constraint limitePorPeriodo(ConstraintFactory fabrica) {
        return servicosNoMes(fabrica)
                .groupBy((pessoa, evento) -> pessoa, ConstraintCollectors.countDistinct((pessoa, evento) -> evento))
                .join(ParametrosDaEscala.class)
                .filter((pessoa, eventos, parametros) -> eventos > parametros.limitePorMes())
                .penalize(
                        HardMediumSoftScore.ONE_HARD,
                        (pessoa, eventos, parametros) -> eventos - parametros.limitePorMes())
                .asConstraint(TipoDeRegra.LIMITE_POR_PERIODO.name());
    }

    /** Mais pessoas do nível limitado no evento que o máximo (o nível de cada uma é o da função da vaga). */
    Constraint maxPorNivelNoEvento(ConstraintFactory fabrica) {
        return fabrica.forEach(VagaPlanejada.class)
                .join(
                        ParametrosDaEscala.class,
                        Joiners.filtering((vaga, parametros) -> parametros.nivelLimitado() != null
                                && parametros.nivelLimitado().equals(vaga.getNivelId())))
                .groupBy(
                        (vaga, parametros) -> vaga.getEvento(),
                        (vaga, parametros) -> parametros,
                        ConstraintCollectors.countBi())
                .filter((evento, parametros, pessoas) -> pessoas > parametros.maximoDoNivel())
                .penalize(
                        HardMediumSoftScore.ONE_HARD,
                        (evento, parametros, pessoas) -> pessoas - parametros.maximoDoNivel())
                .asConstraint(TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO.name());
    }

    /** Vaga vazia: a obrigatória pesa 100 mais os dias até o fim do mês; a opcional, 1. */
    Constraint prioridadePorData(ConstraintFactory fabrica) {
        return fabrica.forEachIncludingUnassigned(VagaPlanejada.class)
                .filter(vaga -> vaga.getPessoa() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM, VagaPlanejada::getPesoSeVazia)
                .asConstraint(TipoDeRegra.PRIORIDADE_POR_DATA.name());
    }

    /** O quadrado dos eventos de cada pessoa no mês: 2 + 2 pesa menos que 3 + 1. */
    Constraint equilibrioDeCarga(ConstraintFactory fabrica) {
        return servicosNoMes(fabrica)
                .groupBy((pessoa, evento) -> pessoa, ConstraintCollectors.countDistinct((pessoa, evento) -> evento))
                .penalize(HardMediumSoftScore.ONE_SOFT, (pessoa, eventos) -> eventos * eventos)
                .asConstraint(TipoDeRegra.EQUILIBRIO_DE_CARGA.name());
    }

    /** (pessoa, evento) de cada vaga preenchida e de cada compromisso que conta no período. */
    private static BiConstraintStream<Long, Long> servicosNoMes(ConstraintFactory fabrica) {
        return fabrica.forEach(VagaPlanejada.class)
                .map(vaga -> vaga.getPessoa().id(), vaga -> vaga.getEvento().id())
                .concat(fabrica.forEach(CompromissoFixo.class)
                        .filter(CompromissoFixo::contaNoPeriodo)
                        .map(CompromissoFixo::pessoaId, CompromissoFixo::eventoId));
    }
}
