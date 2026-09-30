package br.igreja.escala.ministerio.domain;

import java.util.Locale;

/** Cor da etiqueta do ministério (docs/design/README.md, "Cor"): tokens tint-mint, tint-rose e tint-lemon. */
public enum CorDoMinisterio {
    MINT("Verde"),
    ROSE("Rosa"),
    LEMON("Amarelo");

    private final String rotulo;

    CorDoMinisterio(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Valor da opção {@code tint} do Badge (templates/componentes/badge.html). */
    public String tint() {
        return name().toLowerCase(Locale.ROOT);
    }
}
