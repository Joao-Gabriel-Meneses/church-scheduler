package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;

/**
 * MAX_POR_NIVEL_NO_EVENTO: quantas pessoas do nível, no máximo, servem no mesmo evento (na Mídia, um Iniciante). O
 * nível é o que a pessoa tem na função da vaga. Sem nível, a regra não vale (é o padrão: desligada).
 */
public record MaxPorNivelParams(Long nivelId, int maximo) implements ParametrosDeRegra {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 20;
    public static final MaxPorNivelParams PADRAO = new MaxPorNivelParams(null, 1);

    public MaxPorNivelParams {
        Exigencias.entre(maximo, MINIMO, MAXIMO, "maximo");
    }
}
