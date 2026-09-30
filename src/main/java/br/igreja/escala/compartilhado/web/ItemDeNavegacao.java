package br.igreja.escala.compartilhado.web;

/**
 * Item de NavPills, da barra inferior do celular ou da SideRail (fragmentos em templates/componentes/navegacao.html).
 *
 * @param rotulo texto da pílula e aria-label na rail ("Disponibilidade")
 * @param rotuloCurto texto da barra inferior do celular, que tem pouco espaço ("Disponib.")
 * @param icone nome do ícone Lucide, que precisa estar em src/main/frontend/icones.json
 * @param url caminho no app, sem o contexto ("/membros")
 * @param atual se é a página ou o ministério atual (aria-current)
 */
public record ItemDeNavegacao(String rotulo, String rotuloCurto, String icone, String url, boolean atual) {}
