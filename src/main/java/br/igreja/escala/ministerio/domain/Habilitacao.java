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

/** O que o membro pode fazer: uma função, num nível do mesmo ministério. Uma por usuário e função. */
@Entity
@Table(name = "habilitacao")
public class Habilitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcao_id", nullable = false, updatable = false)
    private Funcao funcao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nivel_id", nullable = false)
    private Nivel nivel;

    protected Habilitacao() {}

    public Habilitacao(Long usuarioId, Funcao funcao, Nivel nivel) {
        this.usuarioId = Exigencias.presente(usuarioId, "usuarioId");
        this.funcao = Exigencias.presente(funcao, "funcao");
        mudarNivel(nivel);
    }

    /** O nível precisa ser do ministério da função: cada ministério tem os seus. */
    public void mudarNivel(Nivel nivel) {
        Exigencias.presente(nivel, "nivel");
        if (!nivel.getMinisterio().mesmoQue(funcao.getMinisterio())) {
            throw new IllegalArgumentException("o nível precisa ser do mesmo ministério da função");
        }
        this.nivel = nivel;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Funcao getFuncao() {
        return funcao;
    }

    public Nivel getNivel() {
        return nivel;
    }
}
