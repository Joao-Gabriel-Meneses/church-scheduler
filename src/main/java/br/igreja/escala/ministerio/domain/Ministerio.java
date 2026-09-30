package br.igreja.escala.ministerio.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ministerio")
public class Ministerio {

    public static final int TAMANHO_NOME = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = TAMANHO_NOME)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CorDoMinisterio cor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Icone icone;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Ministerio() {}

    public Ministerio(String nome, CorDoMinisterio cor, Icone icone) {
        alterar(nome, cor, icone);
    }

    public void alterar(String nome, CorDoMinisterio cor, Icone icone) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.cor = Exigencias.presente(cor, "cor");
        this.icone = Exigencias.presente(icone, "icone");
    }

    /**
     * Mesmo ministério, pelo id. Usa {@code getId()} e não o campo porque o outro lado pode ser um proxy do Hibernate
     * ainda não carregado.
     */
    public boolean mesmoQue(Ministerio outro) {
        return this == outro || (outro != null && getId() != null && getId().equals(outro.getId()));
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public CorDoMinisterio getCor() {
        return cor;
    }

    public Icone getIcone() {
        return icone;
    }
}
