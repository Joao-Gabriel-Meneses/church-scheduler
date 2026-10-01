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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/** Usuário × ministério. O usuário é de outro módulo (identidade), então fica só o id. */
@Entity
@Table(name = "membresia")
public class Membresia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministerio_id", nullable = false, updatable = false)
    private Ministerio ministerio;

    @Column(nullable = false)
    private boolean gerente;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Membresia() {}

    public Membresia(Long usuarioId, Ministerio ministerio) {
        this.usuarioId = Exigencias.presente(usuarioId, "usuarioId");
        this.ministerio = Exigencias.presente(ministerio, "ministerio");
    }

    public void tornarGerente() {
        this.gerente = true;
    }

    public void removerGerente() {
        this.gerente = false;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Ministerio getMinisterio() {
        return ministerio;
    }

    public boolean isGerente() {
        return gerente;
    }
}
