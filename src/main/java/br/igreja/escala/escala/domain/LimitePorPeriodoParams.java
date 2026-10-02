package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;

/** LIMITE_POR_PERIODO: em quantos eventos do mês, no máximo, cada pessoa serve no ministério. */
public record LimitePorPeriodoParams(int maximo) implements ParametrosDeRegra {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 31;
    public static final int PADRAO = 3;

    public LimitePorPeriodoParams {
        Exigencias.entre(maximo, MINIMO, MAXIMO, "maximo");
    }
}
