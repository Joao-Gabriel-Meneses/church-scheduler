# ScheduleGrid
A escala do período: uma linha por evento, uma coluna por função, cada célula com uma `Slot`.

- Markup: `rt-table rt-sched` dentro de `rt-panel`. Primeira coluna: data (dia em `heading`-ish 20px + dia da semana em `caption`) e evento + horário.
- Colunas vêm das funções do ministério (Projeção, Transmissão); funções com `qtd_max` > 1 empilham várias `Slot` na célula.
- Linha com vaga vazia ganha `is-alert` (filete `alert` à esquerda) — além do texto "Vaga vazia" na célula.
- No celular vira lista de cartões por evento, funções uma abaixo da outra.
- Em rascunho as células são clicáveis para trocar pessoa; publicada, só leitura para membros.
