package br.igreja.escala.escala.solver;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Quem serve no ministério, como valor da vaga: o nível em cada função em que é habilitada e os eventos em que marcou
 * Pode (sem resposta conta como não pode). Igual por id, porque o solver compara pessoas o tempo todo.
 */
public record Pessoa(Long id, String nome, Map<Long, Long> nivelPorFuncao, Set<Long> eventosQuePode) {

    public Pessoa {
        Objects.requireNonNull(id, "id");
        nivelPorFuncao = Map.copyOf(nivelPorFuncao);
        eventosQuePode = Set.copyOf(eventosQuePode);
    }

    public boolean habilitadaEm(Long funcaoId) {
        return nivelPorFuncao.containsKey(funcaoId);
    }

    /** O nível na função, ou nulo se não é habilitada nela. */
    public Long nivelEm(Long funcaoId) {
        return nivelPorFuncao.get(funcaoId);
    }

    public boolean pode(Long eventoId) {
        return eventosQuePode.contains(eventoId);
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Pessoa pessoa && id.equals(pessoa.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return nome + " (" + id + ")";
    }
}
