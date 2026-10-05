package br.igreja.escala.compartilhado.web;

import java.util.List;

/**
 * Item de NavPills, da barra inferior do celular ou da SideRail (fragmentos em templates/componentes/navegacao.html).
 *
 * @param rotulo texto da pílula e aria-label na rail ("Disponibilidade")
 * @param rotuloCurto texto da barra inferior do celular, que tem pouco espaço ("Disponib.")
 * @param icone nome do ícone Lucide, que precisa estar em src/main/frontend/icones.json
 * @param url caminho no app, sem o contexto ("/membros"); num menu, o do primeiro filho
 * @param atual se é a página ou o ministério atual (aria-current); num menu, se algum filho é
 * @param separado num menu, se o item abre um grupo novo (linha antes dele)
 * @param filhos com itens, a aba vira um menu (NavMenu) que abre essa lista
 */
public record ItemDeNavegacao(
        String rotulo,
        String rotuloCurto,
        String icone,
        String url,
        boolean atual,
        boolean separado,
        List<ItemDeNavegacao> filhos) {

    public ItemDeNavegacao {
        filhos = List.copyOf(filhos);
    }

    /** Item simples, sem lista. */
    public ItemDeNavegacao(String rotulo, String rotuloCurto, String icone, String url, boolean atual) {
        this(rotulo, rotuloCurto, icone, url, atual, false, List.of());
    }

    /** Se a aba abre uma lista de opções. */
    public boolean isMenu() {
        return !filhos.isEmpty();
    }

    /** Aba que abre a lista {@code filhos}; fica atual quando algum filho é. */
    static ItemDeNavegacao menu(String rotulo, String rotuloCurto, String icone, List<ItemDeNavegacao> filhos) {
        return new ItemDeNavegacao(
                rotulo,
                rotuloCurto,
                icone,
                filhos.getFirst().url(),
                filhos.stream().anyMatch(ItemDeNavegacao::atual),
                false,
                filhos);
    }
}
