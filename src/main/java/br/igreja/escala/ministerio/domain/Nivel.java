package br.igreja.escala.ministerio.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Nível de experiência do ministério (Iniciante, Experiente). A ordem vai do menos para o mais experiente. */
@Entity
@Table(name = "nivel")
public class Nivel {

    public static final int TAMANHO_NOME = 40;
    public static final int ORDEM_MAXIMA = 999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministerio_id", nullable = false, updatable = false)
    private Ministerio ministerio;

    @Column(nullable = false, length = TAMANHO_NOME)
    private String nome;

    @Column(nullable = false)
    private int ordem;

    protected Nivel() {}

    public Nivel(Ministerio ministerio, String nome, int ordem) {
        this.ministerio = Exigencias.presente(ministerio, "ministerio");
        alterar(nome, ordem);
    }

    public void alterar(String nome, int ordem) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.ordem = Exigencias.entre(ordem, 1, ORDEM_MAXIMA, "ordem");
    }

    public Long getId() {
        return id;
    }

    public Ministerio getMinisterio() {
        return ministerio;
    }

    public String getNome() {
        return nome;
    }

    public int getOrdem() {
        return ordem;
    }
}
