package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;

/**
 * Função na lista do gerente.
 *
 * @param habilitados quantos membros estão habilitados nela
 */
public record FuncaoResumo(Long id, String nome, Icone icone, int qtdMin, int qtdMax, long habilitados) {

    static FuncaoResumo de(Funcao funcao, long habilitados) {
        return new FuncaoResumo(
                funcao.getId(),
                funcao.getNome(),
                funcao.getIcone(),
                funcao.getQtdMin(),
                funcao.getQtdMax(),
                habilitados);
    }

    /** "1 pessoa por evento", "De 1 a 2 pessoas por evento", "Até 2 pessoas por evento". */
    public String pessoasPorEvento() {
        if (qtdMin == qtdMax) {
            return pessoas(qtdMax) + " por evento";
        }
        if (qtdMin == 0) {
            return "Até " + pessoas(qtdMax) + " por evento";
        }
        return "De " + qtdMin + " a " + pessoas(qtdMax) + " por evento";
    }

    public String descricaoDosHabilitados() {
        return habilitados + (habilitados == 1 ? " habilitado" : " habilitados");
    }

    private static String pessoas(int quantidade) {
        return quantidade + (quantidade == 1 ? " pessoa" : " pessoas");
    }
}
