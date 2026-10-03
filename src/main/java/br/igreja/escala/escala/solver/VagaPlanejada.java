package br.igreja.escala.escala.solver;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import java.time.LocalDateTime;

/**
 * A vaga como o solver a vê: separada da entidade JPA {@code Vaga}, porque o solver clona a solução e roda numa thread
 * sem sessão do Hibernate. A pessoa pode ficar vazia (allowsUnassigned) quando ninguém cabe sem violar regra rígida.
 */
@PlanningEntity
public class VagaPlanejada {

    /** Peso da vaga obrigatória vazia, antes da distância ao fim do mês: preencher mais vagas vale mais que a data. */
    static final int PESO_DA_OBRIGATORIA = 100;

    @PlanningId
    private Long id;

    private EventoDaEscala evento;
    private FuncaoDaEscala funcao;
    private int posicao;

    @PlanningPin
    private boolean presa;

    @PlanningVariable(allowsUnassigned = true)
    private Pessoa pessoa;

    /** Para o Timefold clonar. */
    public VagaPlanejada() {}

    public VagaPlanejada(
            Long id, EventoDaEscala evento, FuncaoDaEscala funcao, int posicao, boolean presa, Pessoa pessoa) {
        this.id = id;
        this.evento = evento;
        this.funcao = funcao;
        this.posicao = posicao;
        this.presa = presa;
        this.pessoa = pessoa;
    }

    /** Até o {@code qtdMin} da função; acima dele a posição é opcional. */
    public boolean isObrigatoria() {
        return posicao <= funcao.qtdMin();
    }

    /** O nível da pessoa na função da vaga, ou nulo (vazia ou sem habilitação). */
    public Long getNivelId() {
        return pessoa == null ? null : pessoa.nivelEm(funcao.id());
    }

    /** Quanto pesa ficar vazia (PRIORIDADE_POR_DATA). */
    public int getPesoSeVazia() {
        return isObrigatoria() ? PESO_DA_OBRIGATORIA + evento.diasAteOFimDoMes() : 1;
    }

    public LocalDateTime getInicio() {
        return evento.inicio();
    }

    public LocalDateTime getFim() {
        return evento.fim();
    }

    public Long getId() {
        return id;
    }

    public EventoDaEscala getEvento() {
        return evento;
    }

    public FuncaoDaEscala getFuncao() {
        return funcao;
    }

    public int getPosicao() {
        return posicao;
    }

    public boolean isPresa() {
        return presa;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public void setPessoa(Pessoa pessoa) {
        this.pessoa = pessoa;
    }

    @Override
    public String toString() {
        return funcao.nome() + " " + posicao + " em " + evento.nome() + " " + evento.inicio() + ": " + pessoa;
    }
}
