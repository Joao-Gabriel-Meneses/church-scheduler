package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Uma vaga da escala: evento × função × posição, com a pessoa escalada ou vazia. Evento, função e pessoa são de outros
 * módulos, então ficam só os ids. A geração mexe só nas vagas que não estão fixadas nem forçadas.
 */
@Entity
@Table(name = "vaga")
public class Vaga {

    public static final int TAMANHO_JUSTIFICATIVA = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Column(name = "funcao_id", nullable = false, updatable = false)
    private Long funcaoId;

    @Column(nullable = false, updatable = false)
    private int posicao;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private boolean fixada;

    @Column(nullable = false)
    private boolean forcada;

    @Column(length = TAMANHO_JUSTIFICATIVA)
    private String justificativa;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Vaga() {}

    /** Vaga vazia. */
    public Vaga(Long eventoId, Long funcaoId, int posicao) {
        this.eventoId = Exigencias.presente(eventoId, "eventoId");
        this.funcaoId = Exigencias.presente(funcaoId, "funcaoId");
        this.posicao = Exigencias.entre(posicao, 1, 99, "posicao");
    }

    /**
     * Põe a pessoa (ou ninguém, com nulo) numa vaga que a geração pode mexer.
     *
     * @throws IllegalStateException se a vaga está fixada ou forçada
     */
    public void escalar(Long usuarioId) {
        if (isPresa()) {
            throw new IllegalStateException("vaga " + id + " está fixada ou forçada");
        }
        this.usuarioId = usuarioId;
    }

    /** Fixada ou forçada: gerar de novo não muda a pessoa (@PlanningPin). */
    public boolean isPresa() {
        return fixada || forcada;
    }

    public boolean isVazia() {
        return usuarioId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Long getFuncaoId() {
        return funcaoId;
    }

    public int getPosicao() {
        return posicao;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public boolean isFixada() {
        return fixada;
    }

    public boolean isForcada() {
        return forcada;
    }

    public String getJustificativa() {
        return justificativa;
    }
}
