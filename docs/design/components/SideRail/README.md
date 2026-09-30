# SideRail
Coluna de botões circulares à esquerda para trocar de ministério; o atual vira pílula escura alongada.

- Markup: `<nav class="rt-rail">` com `<a class="rt-icon-btn" aria-label="<ministério>">`; o atual com `aria-current="page"`.
- Mostra só os ministérios da pessoa. Com um único ministério, esconda a rail.
- Só desktop; no celular o ministério vira um seletor no topo.
- Cada item tem `aria-label` com o nome do ministério e um `Tooltip` com o mesmo nome no hover e no foco do teclado.
