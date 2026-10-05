package br.igreja.escala.compartilhado.domain;

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

/** Registro imutável de uma ação de gerente ou admin. Usuários e ministério ficam só pelo id (outros módulos). */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    public static final int TAMANHO_DESCRICAO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(name = "ministerio_id", updatable = false)
    private Long ministerioId;

    @Column(name = "alvo_usuario_id", updatable = false)
    private Long alvoUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AcaoAuditada acao;

    @Column(nullable = false, length = TAMANHO_DESCRICAO, updatable = false)
    private String descricao;

    @Column(length = 45, updatable = false)
    private String ip;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Auditoria() {}

    public Auditoria(RegistroDeAuditoria registro) {
        this(registro, null);
    }

    /** @param ip de onde veio a ação; {@code null} se não veio de uma requisição */
    public Auditoria(RegistroDeAuditoria registro, String ip) {
        this.ip = ip;
        this.acao = Exigencias.presente(registro.acao(), "acao");
        this.autorId = Exigencias.presente(registro.autorId(), "autorId");
        this.ministerioId = registro.ministerioId();
        this.alvoUsuarioId = registro.alvoUsuarioId();
        this.descricao = Exigencias.texto(registro.descricao(), "descricao", TAMANHO_DESCRICAO);
    }

    public Long getId() {
        return id;
    }

    public Long getAutorId() {
        return autorId;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public Long getAlvoUsuarioId() {
        return alvoUsuarioId;
    }

    public AcaoAuditada getAcao() {
        return acao;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getIp() {
        return ip;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
