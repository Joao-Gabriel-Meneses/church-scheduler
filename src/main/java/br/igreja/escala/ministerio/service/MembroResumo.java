package br.igreja.escala.ministerio.service;

import br.igreja.escala.identidade.service.UsuarioResumo;
import java.util.List;

/**
 * Membro de um ministério, como o gerente o vê.
 *
 * @param habilitacoes "Função · Nível", em ordem de função
 */
public record MembroResumo(UsuarioResumo pessoa, boolean gerente, List<String> habilitacoes) {

    public Long id() {
        return pessoa.id();
    }

    public String nome() {
        return pessoa.nome();
    }

    /** "Projeção · Experiente, Transmissão · Iniciante", para a ListRow do celular. */
    public String descricaoDasHabilitacoes() {
        return habilitacoes.isEmpty() ? "Sem habilitação ainda" : String.join(", ", habilitacoes);
    }
}
