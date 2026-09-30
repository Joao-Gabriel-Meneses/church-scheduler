# Tooltip
Nome de um controle que só tem ícone, como os ministérios da SideRail. Não veio do Claude Design; foi criado no app seguindo os tokens e fica em `tooltip.css`.

- Pílula em `ink` com texto `on-ink` em `label` (12.6:1), `shadow-float`, à direita do controle, afastada `space-2`.
- Aparece no hover e no foco do teclado (`:focus-visible`), não só no hover. Continua visível com o ponteiro em cima dela, e o Esc a esconde sem tirar o foco (WCAG 1.4.13).
- Não substitui o nome acessível: o controle mantém o `aria-label`, e o texto do tooltip é `aria-hidden` para não ser lido duas vezes. Sem `title`, que abriria um segundo tooltip do navegador.
- Markup: o controle ganha `rt-com-tooltip` e, dentro dele, `<span class="rt-tooltip" aria-hidden="true">Mídia</span>`.
- Só no desktop: no celular não há hover, e a rail vira o seletor de ministério, que já mostra os nomes.
