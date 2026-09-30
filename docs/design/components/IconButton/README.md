# IconButton
Botão circular com um ícone de traço — o gesto básico do sistema.

- Markup: `<button class="rt-icon-btn" aria-label="…">` + um SVG de 20px (`size-icon`), traço 1.5px, `currentColor`.
- Variantes: padrão (`surface` + `ink`), `--dark` (`ink` + `on-ink`) para o estado ativo ou a ação principal de um grupo (ex.: travar período).
- Tamanhos: `--sm` (`size-control-sm`, só no desktop, dentro de cartões), padrão (`size-control`, 44px — mínimo no celular), `--lg` (`size-control-lg`).
- Sempre dê `aria-label` em pt-BR ("Travar disponibilidade", "Pedir troca").
