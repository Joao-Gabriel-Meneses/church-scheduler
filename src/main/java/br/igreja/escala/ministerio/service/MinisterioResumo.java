package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import java.util.List;

/**
 * Ministério na lista do admin.
 *
 * @param gerentes nomes dos gerentes, em ordem
 * @param membros quantas pessoas estão no ministério, gerentes incluídos
 */
public record MinisterioResumo(
        Long id, String nome, CorDoMinisterio cor, Icone icone, List<String> gerentes, long membros) {

    /** "Gerente: Ana Souza · 18 membros", para a ListRow do celular. */
    public String descricao() {
        return descricaoDosGerentes() + " · " + descricaoDosMembros();
    }

    public String descricaoDosGerentes() {
        if (gerentes.isEmpty()) {
            return "Sem gerente";
        }
        return (gerentes.size() == 1 ? "Gerente: " : "Gerentes: ") + String.join(", ", gerentes);
    }

    public String descricaoDosMembros() {
        return membros + (membros == 1 ? " membro" : " membros");
    }
}
