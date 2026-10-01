package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.Icone;

/**
 * Uma função do ministério no formulário de habilitações de um membro.
 *
 * @param nivelId o nível atual do membro na função, ou nulo se ele não está habilitado nela
 */
public record LinhaDeHabilitacao(Long funcaoId, String funcao, Icone icone, Long nivelId) {

    /** Nome do campo do formulário: "nivel-{funcaoId}". */
    public String campo() {
        return HabilitacaoService.CAMPO + funcaoId;
    }
}
