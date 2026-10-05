package br.igreja.escala.escala.service;

import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import java.util.List;

/**
 * Quem é habilitado na função da vaga, com o que o solver diz se for escalado aqui: livre (nenhuma regra), forçável
 * (só regras forçáveis, com justificativa) ou bloqueado (alguma regra que nunca se força).
 *
 * @param nivel o nível na função, ou nulo
 * @param escalasNoMes em quantos eventos do mês já serve, sem contar esta vaga
 * @param atual é quem está na vaga agora
 */
public record Candidato(
        Long usuarioId,
        String nome,
        String iniciais,
        String nivel,
        long escalasNoMes,
        List<Violacao> violacoes,
        boolean atual) {

    public boolean isLivre() {
        return violacoes.isEmpty();
    }

    public boolean isForcavel() {
        return !violacoes.isEmpty() && violacoes.stream().allMatch(Violacao::forcavel);
    }

    public boolean isBloqueado() {
        return violacoes.stream().anyMatch(violacao -> !violacao.forcavel());
    }

    /** "Experiente · 2 escalas no mês". */
    public String descricao() {
        String escalas = escalasNoMes == 0
                ? "nenhuma escala no mês"
                : escalasNoMes + (escalasNoMes == 1 ? " escala no mês" : " escalas no mês");
        return nivel == null ? escalas : nivel + " · " + escalas;
    }

    /** As regras violadas, "LIMITE_POR_PERIODO · DISPONIBILIDADE". */
    public String regras() {
        return String.join(
                " · ",
                violacoes.stream().map(violacao -> violacao.regra().name()).toList());
    }

    /** Os porquês, um depois do outro. */
    public String porQue() {
        return String.join(" ", violacoes.stream().map(Violacao::porQue).toList());
    }
}
