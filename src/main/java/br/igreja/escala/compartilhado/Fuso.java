package br.igreja.escala.compartilhado;

import java.time.ZoneId;

/** Fuso oficial do sistema. Nunca use o fuso padrão da JVM. */
public final class Fuso {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private Fuso() {}
}
