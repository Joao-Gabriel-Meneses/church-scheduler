package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Uma regra do catálogo configurada num ministério. Os parâmetros ficam em JSON (CLOB com {@code IS JSON}, porque o
 * Oracle 19c não tem o tipo JSON) e são lidos pelo record do tipo, que os valida.
 */
@Entity
@Table(name = "regra")
public class Regra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ministerio_id", nullable = false, updatable = false)
    private Long ministerioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private TipoDeRegra tipo;

    @Lob
    @Column(nullable = false)
    private String parametros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Rigidez rigidez;

    @Column(nullable = false)
    private int peso;

    @Column(nullable = false)
    private boolean ativa;

    protected Regra() {}

    /** A regra com o padrão do catálogo. */
    public Regra(Long ministerioId, TipoDeRegra tipo) {
        this.ministerioId = Exigencias.presente(ministerioId, "ministerioId");
        this.tipo = Exigencias.presente(tipo, "tipo");
        this.rigidez = tipo.rigidezPadrao();
        this.peso = 1;
        alterar(tipo.parametrosPadrao(), tipo.ativaPorPadrao());
    }

    /**
     * @throws IllegalArgumentException se os parâmetros não são do tipo ou se tenta desligar uma regra que não se desliga
     */
    public void alterar(ParametrosDeRegra parametros, boolean ativa) {
        Exigencias.presente(parametros, "parametros");
        if (!tipo.classeDosParametros().isInstance(parametros)) {
            throw new IllegalArgumentException(
                    tipo + " não usa " + parametros.getClass().getSimpleName());
        }
        if (!ativa && tipo.sempreAtiva()) {
            throw new IllegalArgumentException(tipo + " não se desliga");
        }
        this.parametros = ParametrosEmJson.escrever(parametros);
        this.ativa = ativa;
    }

    /**
     * @throws IllegalArgumentException se o JSON gravado não vale para o tipo
     */
    public ParametrosDeRegra getParametros() {
        return ParametrosEmJson.ler(parametros, tipo.classeDosParametros());
    }

    /** A regra para a geração, sem o vínculo com o banco. */
    public RegraVigente vigente() {
        return new RegraVigente(tipo, rigidez, peso, ativa, getParametros());
    }

    public Long getId() {
        return id;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public TipoDeRegra getTipo() {
        return tipo;
    }

    public Rigidez getRigidez() {
        return rigidez;
    }

    public int getPeso() {
        return peso;
    }

    public boolean isAtiva() {
        return ativa;
    }
}
