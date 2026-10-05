package br.igreja.escala.escala.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Uma vaga da escala: evento × função × posição, com a pessoa escalada ou vazia. Evento, função e pessoa são de outros
 * módulos, então ficam só os ids. A geração mexe só nas vagas que não estão fixadas nem forçadas; toda alteração do
 * gerente fixa a vaga, e a forçada (que viola uma regra forçável, com justificativa) é sempre fixada. A versão é o
 * controle de concorrência: quem edita manda a versão que viu.
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

    @Version
    @Column(nullable = false)
    private long versao;

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

    /** O gerente põe a pessoa (ou ninguém, com nulo) sem violar regra: a vaga fica fixada e deixa de ser forçada. */
    public void ajustar(Long usuarioId) {
        this.usuarioId = usuarioId;
        this.fixada = true;
        this.forcada = false;
        this.justificativa = null;
    }

    /**
     * O gerente põe a pessoa contra uma regra forçável: a vaga fica forçada e fixada, com a justificativa.
     *
     * @throws IllegalArgumentException sem pessoa ou sem justificativa
     */
    public void forcar(Long usuarioId, String justificativa) {
        this.usuarioId = Exigencias.presente(usuarioId, "usuarioId");
        this.justificativa = Exigencias.texto(justificativa, "justificativa", TAMANHO_JUSTIFICATIVA);
        this.fixada = true;
        this.forcada = true;
    }

    /** Fixa a vaga como está, preenchida ou vazia: gerar de novo não a muda. */
    public void fixar() {
        this.fixada = true;
    }

    /** Solta a vaga para a próxima geração. A forçada deixa de ser forçada; a pessoa fica até gerar de novo. */
    public void desafixar() {
        this.fixada = false;
        this.forcada = false;
        this.justificativa = null;
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

    public long getVersao() {
        return versao;
    }
}
