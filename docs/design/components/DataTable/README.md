# DataTable
Tabela de cadastro (membros, eventos, auditoria) com células de duas linhas e hover em `brand-tint`.

- Markup: `<table class="rt-table">` dentro de `rt-panel`. Cabeçalho em `label`/`ink-muted`; linhas `surface` separadas por `line`.
- Célula de duas linhas: `rt-cell-2` com valor em `body` e complemento em `rt-caption`.
- Pessoas: `rt-avatar` com iniciais + nome. Estado: `rt-dot` + texto, ou `Badge --alert` para o que precisa de ação.
- No celular, esconda colunas secundárias e mantenha nome + estado.
