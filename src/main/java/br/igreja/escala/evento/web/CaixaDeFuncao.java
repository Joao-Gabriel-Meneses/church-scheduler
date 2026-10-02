package br.igreja.escala.evento.web;

import br.igreja.escala.ministerio.domain.Funcao;
import java.util.List;

/** Uma função do ministério no formulário de evento ou de modelo, marcada se o evento precisa dela. */
public record CaixaDeFuncao(Long id, String nome, boolean marcada) {

    static List<CaixaDeFuncao> de(List<Funcao> funcoes, List<Long> marcadas) {
        return funcoes.stream()
                .map(funcao -> new CaixaDeFuncao(
                        funcao.getId(), funcao.getNome(), marcadas != null && marcadas.contains(funcao.getId())))
                .toList();
    }
}
