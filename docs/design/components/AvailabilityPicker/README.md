# AvailabilityPicker
Lista dos eventos do mês para o membro marcar, com um toque por evento: Pode, Prefiro não ou Não pode.

- Markup: `rt-avail` > `rt-avail__row` (data, evento + horário, `rt-choice`). Cada botão tem `aria-pressed` e `aria-label` em texto.
- Pode = `ink` cheio com ✓; Não pode = contorno `ink` com ✕; Prefiro não = `brand-tint` (só quando a regra PREFERENCIA estiver ativa; senão, esconda o botão).
- Linha sem resposta fica com os três botões neutros; o cabeçalho mostra "4 de 5 respondidos".
- Salva a cada toque (htmx), sem botão de salvar — meta: mês inteiro em menos de 1 minuto.
- Período travado: `rt-avail--locked` desativa tudo e um `Badge --locked` explica.
