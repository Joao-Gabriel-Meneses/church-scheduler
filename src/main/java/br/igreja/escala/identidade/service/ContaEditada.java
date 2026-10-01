package br.igreja.escala.identidade.service;

import java.util.List;

/**
 * @param camposAlterados "nome", "e-mail" e "telefone", na ordem do formulário; vazio se nada mudou
 */
public record ContaEditada(UsuarioResumo conta, List<String> camposAlterados) {

    public boolean mudou() {
        return !camposAlterados.isEmpty();
    }

    /**
     * "e-mail alterado", "nome e e-mail alterados", "nome, e-mail e telefone alterados", para a auditoria.
     *
     * @throws IllegalStateException se nada mudou
     */
    public String resumo() {
        if (!mudou()) {
            throw new IllegalStateException("Nenhum campo da conta mudou");
        }
        int ultimo = camposAlterados.size() - 1;
        String campos = ultimo == 0
                ? camposAlterados.getFirst()
                : String.join(", ", camposAlterados.subList(0, ultimo)) + " e " + camposAlterados.get(ultimo);
        return campos + (ultimo == 0 ? " alterado" : " alterados");
    }
}
