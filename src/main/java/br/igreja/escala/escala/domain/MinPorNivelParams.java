package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;

/**
 * MIN_POR_NIVEL_NO_EVENTO: quantas pessoas do nível, no mínimo, servem em cada evento que tem alguém escalado (na
 * Mídia, um Experiente). O nível é o que a pessoa tem na função da vaga. Sem nível, a regra não vale (é o padrão:
 * desligada).
 */
public record MinPorNivelParams(Long nivelId, int minimo) implements ParametrosDeRegra {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 20;
    public static final MinPorNivelParams PADRAO = new MinPorNivelParams(null, 1);

    public MinPorNivelParams {
        Exigencias.entre(minimo, MINIMO, MAXIMO, "minimo");
    }
}
