package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** As funções marcadas no formulário de um evento ou de um modelo, conferidas com as do ministério. */
final class FuncoesEscolhidas {

    static final String CAMPO = "funcoes";

    private FuncoesEscolhidas() {}

    /**
     * @param escolhidas ids marcados no formulário
     * @param doMinisterio ids de todas as funções do ministério
     * @return o que gravar: vazio quando são todas as do ministério, que é o padrão
     * @throws NaoEncontradoException se alguma função não é do ministério
     * @throws RegraVioladaException no campo {@code funcoes}, se o ministério tem funções e nenhuma foi marcada
     */
    static Set<Long> paraGravar(Collection<Long> escolhidas, Collection<Long> doMinisterio) {
        var marcadas = new HashSet<>(escolhidas);
        marcadas.stream().filter(id -> !doMinisterio.contains(id)).findAny().ifPresent(id -> {
            throw new NaoEncontradoException("Função " + id + " fora do ministério");
        });
        if (marcadas.isEmpty() && !doMinisterio.isEmpty()) {
            throw new RegraVioladaException(CAMPO, "Escolha ao menos uma função.");
        }
        return marcadas.containsAll(doMinisterio) ? Set.of() : marcadas;
    }
}
