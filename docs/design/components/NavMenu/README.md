# NavMenu
Aba que abre uma lista de opções, para a navegação ter poucas abas e sempre as mesmas. Não veio do Claude Design; foi criado no app seguindo os tokens e fica em `nav-menu.css`.

- É um `<details class="rt-nav-menu">` com `<summary class="rt-nav__item">` (ícone, rótulo e seta `chevron-down`, que gira ao abrir). Abre e fecha sem JS; o `app.js` fecha com clique fora, com Esc (devolvendo o foco à aba) e quando outro menu abre.
- O painel `rt-nav-menu__painel` é flutuante: fundo `canvas`, borda de 2px `surface`, `radius-lg` e `shadow-float`. Cada opção é um `rt-nav-menu__item` de 44px com ícone de 20px, `radius-control`; a página atual com `aria-current="page"` fica escura.
- `rt-nav-menu__sep` separa grupos (ex.: Ministérios, que é do admin e vale para todos).
- `rt-nav-menu--atual` deixa a aba escura quando uma opção é a página atual. `rt-nav-menu--fim` alinha o painel à direita (menu do usuário).
- `rt-nav-menu--folha`: na barra inferior do celular, o painel sobe da barra na largura toda, com opções de 56px.
- No app: "Gerenciar" e o menu do usuário (`templates/componentes/navegacao.html`).
