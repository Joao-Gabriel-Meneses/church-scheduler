package br.igreja.escala.ministerio.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Função de um ministério (Projeção, Transmissão) e quantas pessoas ela pede por evento. */
@Entity
@Table(name = "funcao")
public class Funcao {

    public static final int TAMANHO_NOME = 60;

    /** Teto de pessoas por função num evento; acima disso é sinal de digitação errada. */
    public static final int QTD_MAXIMA = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministerio_id", nullable = false, updatable = false)
    private Ministerio ministerio;

    @Column(nullable = false, length = TAMANHO_NOME)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Icone icone;

    @Column(name = "qtd_min", nullable = false)
    private int qtdMin;

    @Column(name = "qtd_max", nullable = false)
    private int qtdMax;

    protected Funcao() {}

    public Funcao(Ministerio ministerio, String nome, Icone icone, int qtdMin, int qtdMax) {
        this.ministerio = Exigencias.presente(ministerio, "ministerio");
        alterar(nome, icone, qtdMin, qtdMax);
    }

    public void alterar(String nome, Icone icone, int qtdMin, int qtdMax) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.icone = Exigencias.presente(icone, "icone");
        this.qtdMax = Exigencias.entre(qtdMax, 1, QTD_MAXIMA, "qtdMax");
        this.qtdMin = Exigencias.entre(qtdMin, 0, qtdMax, "qtdMin");
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

    public Icone getIcone() {
        return icone;
    }

    public int getQtdMin() {
        return qtdMin;
    }

    public int getQtdMax() {
        return qtdMax;
    }
}
