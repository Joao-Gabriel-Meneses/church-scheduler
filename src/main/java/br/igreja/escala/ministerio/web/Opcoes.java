package br.igreja.escala.ministerio.web;

import br.igreja.escala.compartilhado.web.Opcao;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import java.util.Arrays;
import java.util.List;

/** Opções dos selects do módulo, a partir dos enums. */
final class Opcoes {

    private Opcoes() {}

    static List<Opcao> cores() {
        return Arrays.stream(CorDoMinisterio.values())
                .map(cor -> new Opcao(cor.name(), cor.rotulo()))
                .toList();
    }

    static List<Opcao> icones() {
        return Arrays.stream(Icone.values())
                .map(icone -> new Opcao(icone.name(), icone.rotulo()))
                .toList();
    }
}
