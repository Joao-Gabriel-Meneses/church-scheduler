# Sheet
Folha que sobe da borda inferior no celular, para ações secundárias sem sair da tela: "Mais ações" da Toolbar e, depois, pedir troca. Não veio do Claude Design; foi criado no app seguindo os tokens e fica em `sheet.css`.

- Fundo `canvas`, cantos superiores `radius-lg`, `shadow-float` (é flutuante) e o fundo da página escurecido com `ink` a 24%.
- Cabeçalho `rt-sheet__cabeca`: título em `heading` e botão circular de fechar (`aria-label="Fechar"`).
- É um popover nativo: `<div id="…" popover class="rt-sheet" role="dialog" aria-label="…">`, aberto por um botão com `popovertarget="…"`. Esc e toque fora fecham, sem JS; o navegador cuida do `aria-expanded` do botão.
- Ações empilhadas em largura total, a primária por último, perto do polegar.
- No app: a Toolbar do gerente usa o Sheet no celular (`templates/componentes/toolbar.html`).
