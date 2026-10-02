package br.igreja.escala.escala.solver;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import java.util.List;

/**
 * O problema de um período de um ministério: as vagas dos eventos por vir, quem serve, os compromissos fixos e as
 * regras (parâmetros e pesos). Hard: regras rígidas; medium: vaga vazia (mais perto pesa mais); soft: equilíbrio.
 */
@PlanningSolution
public class EscalaDoPeriodo {

    private Long periodoId;

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<Pessoa> pessoas;

    @ProblemFactCollectionProperty
    private List<CompromissoFixo> compromissos;

    @ProblemFactProperty
    private ParametrosDaEscala parametros;

    @PlanningEntityCollectionProperty
    private List<VagaPlanejada> vagas;

    /** Rigidez e peso de cada regra do ministério; regra desligada pesa zero (PesosDasRegras). */
    private ConstraintWeightOverrides<HardMediumSoftScore> pesos;

    @PlanningScore
    private HardMediumSoftScore pontuacao;

    /** Para o Timefold clonar. */
    public EscalaDoPeriodo() {}

    public EscalaDoPeriodo(
            Long periodoId,
            List<Pessoa> pessoas,
            List<CompromissoFixo> compromissos,
            ParametrosDaEscala parametros,
            List<VagaPlanejada> vagas,
            ConstraintWeightOverrides<HardMediumSoftScore> pesos) {
        this.periodoId = periodoId;
        this.pessoas = pessoas;
        this.compromissos = compromissos;
        this.parametros = parametros;
        this.vagas = vagas;
        this.pesos = pesos;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public List<Pessoa> getPessoas() {
        return pessoas;
    }

    public List<CompromissoFixo> getCompromissos() {
        return compromissos;
    }

    public ParametrosDaEscala getParametros() {
        return parametros;
    }

    public List<VagaPlanejada> getVagas() {
        return vagas;
    }

    public ConstraintWeightOverrides<HardMediumSoftScore> getPesos() {
        return pesos;
    }

    /** O Timefold 2 exige setter público para os pesos (lidos pelo getter). */
    public void setPesos(ConstraintWeightOverrides<HardMediumSoftScore> pesos) {
        this.pesos = pesos;
    }

    public HardMediumSoftScore getPontuacao() {
        return pontuacao;
    }

    public void setPontuacao(HardMediumSoftScore pontuacao) {
        this.pontuacao = pontuacao;
    }
}
