package br.igreja.escala.escala.solver;

import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;

/**
 * Os parâmetros das regras do ministério, como fato do problema: o limite do mês e o mínimo por nível (nível nulo:
 * regra desligada). Ligar e desligar as outras regras é pelo peso (PesosDasRegras).
 */
public record ParametrosDaEscala(int limitePorMes, Long nivelExigido, int minimoDoNivel) {

    public static ParametrosDaEscala de(RegrasDoMinisterio regras) {
        var minimo = regras.minimoPorNivel().orElse(MinPorNivelParams.PADRAO);
        return new ParametrosDaEscala(regras.limitePorMes(), minimo.nivelId(), minimo.minimo());
    }
}
