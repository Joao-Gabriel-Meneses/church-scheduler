package br.igreja.escala.disponibilidade.domain;

/**
 * O que a pessoa respondeu para um evento. "Prefiro não" existe no modelo, mas só vale quando a regra PREFERENCIA
 * entrar (Fase 5); até lá o serviço recusa.
 */
public enum Resposta {
    PODE("Pode"),
    NAO_PODE("Não pode"),
    PREFERE_NAO("Prefiro não, mas posso");

    private final String rotulo;

    Resposta(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto do botão e da auditoria (docs/design/README.md, "Conteúdo e voz"). */
    public String rotulo() {
        return rotulo;
    }
}
