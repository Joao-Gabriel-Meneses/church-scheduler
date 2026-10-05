# NavPills
Navegação principal no topo: abas brancas com ícone de 16px + rótulo, cantos `radius-control`; a atual fica escura.

- Markup: `<nav class="rt-nav">` com `<a class="rt-nav__item">`; a página atual com `aria-current="page"`.
- As abas não mudam de uma página para outra: **Início** e **Disponibilidade** para todos e, para quem gerencia algum ministério (ou é admin), o `NavMenu` **Gerenciar**, com Escalas, Eventos, Disponibilidade, Membros, Funções e Regras, mais Ministérios (admin) separado por uma linha.
- "Minhas escalas" não é aba: fica no Início.
- No celular, a nav vira barra inferior com os mesmos 3 itens (ícone + rótulo curto); o menu sobe da barra como uma folha.
