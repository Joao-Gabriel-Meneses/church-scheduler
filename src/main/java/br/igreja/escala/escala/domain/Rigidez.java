package br.igreja.escala.escala.domain;

/** O nível da pontuação em que uma regra pesa: rígida nunca é violada sem aviso; as outras só ordenam as soluções. */
public enum Rigidez {
    HARD("Rígida"),
    MEDIUM("Prioridade"),
    SOFT("Preferência");

    private final String rotulo;

    Rigidez(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
