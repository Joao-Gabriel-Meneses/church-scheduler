package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.Nivel;

/**
 * Nível na lista do gerente.
 *
 * @param habilitacoes quantas habilitações (membro × função) estão neste nível
 */
public record NivelResumo(Long id, String nome, int ordem, long habilitacoes) {

    static NivelResumo de(Nivel nivel, long habilitacoes) {
        return new NivelResumo(nivel.getId(), nivel.getNome(), nivel.getOrdem(), habilitacoes);
    }

    public String descricaoDasHabilitacoes() {
        return habilitacoes + (habilitacoes == 1 ? " habilitação" : " habilitações");
    }
}
