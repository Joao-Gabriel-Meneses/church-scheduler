package br.igreja.escala.escala.solver;

import br.igreja.escala.escala.domain.MaxPorNivelParams;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;

/**
 * Os parâmetros das regras do ministério, como fato do problema: o limite do mês e o máximo por nível (nível nulo:
 * regra desligada). Ligar e desligar as outras regras é pelo peso (PesosDasRegras).
 */
public record ParametrosDaEscala(int limitePorMes, Long nivelLimitado, int maximoDoNivel) {

    public static ParametrosDaEscala de(RegrasDoMinisterio regras) {
        var maximo = regras.maximoPorNivel().orElse(MaxPorNivelParams.PADRAO);
        return new ParametrosDaEscala(regras.limitePorMes(), maximo.nivelId(), maximo.maximo());
    }
}
