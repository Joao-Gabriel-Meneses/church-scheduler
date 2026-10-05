package br.igreja.escala.escala.solver;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** O peso de cada restrição vem da regra do ministério: a rigidez dá o nível, o peso multiplica, desligada é zero. */
public final class PesosDasRegras {

    private PesosDasRegras() {}

    public static ConstraintWeightOverrides<HardMediumSoftScore> de(RegrasDoMinisterio regras) {
        Map<String, HardMediumSoftScore> pesos = new HashMap<>();
        for (RegraVigente regra : regras.todas()) {
            pesos.put(regra.tipo().name(), regra.ativa() ? peso(regra) : HardMediumSoftScore.ZERO);
        }
        return ConstraintWeightOverrides.of(pesos);
    }

    /**
     * Só a regra pesa, com o peso dado; as outras pesam zero. A pontuação calculada assim é só a dela (ValidacaoDaVaga).
     */
    public static ConstraintWeightOverrides<HardMediumSoftScore> isolando(TipoDeRegra tipo, HardMediumSoftScore peso) {
        Map<String, HardMediumSoftScore> pesos = new HashMap<>();
        Arrays.stream(TipoDeRegra.values()).forEach(outro -> pesos.put(outro.name(), HardMediumSoftScore.ZERO));
        pesos.put(tipo.name(), peso);
        return ConstraintWeightOverrides.of(pesos);
    }

    private static HardMediumSoftScore peso(RegraVigente regra) {
        return switch (regra.rigidez()) {
            case HARD -> HardMediumSoftScore.ofHard(regra.peso());
            case MEDIUM -> HardMediumSoftScore.ofMedium(regra.peso());
            case SOFT -> HardMediumSoftScore.ofSoft(regra.peso());
        };
    }
}
