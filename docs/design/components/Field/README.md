# Field
Campo de formulário: rótulo, controle em pílula e, quando houver, dica ou erro. Não veio do Claude Design; foi criado no app seguindo os tokens e fica em `field.css` (fora do `bundle.css`, para um novo export não apagá-lo).

- Markup: `rt-field` com `rt-field__label` (`body`, `ink`) e `input.rt-input`. Dica em `rt-field__hint` (`label`, `ink-muted`) ligada por `aria-describedby`.
- Controle: `radius-pill`, altura `size-control` (44px, alvo de toque no celular), fundo `surface`, texto `body-lg` (16px, para o iOS não dar zoom). Placeholder em `ink-subtle`, só como exemplo, nunca no lugar do rótulo.
- Borda de 1px em `ink-muted`, não em `line`: `line` sobre `surface` dá 1.25:1, e a borda que identifica um controle precisa de 3:1 (WCAG 1.4.11). `ink-muted` dá 6.48:1. No hover, `ink`.
- Foco: anel sólido de 2px em `brand-strong`, afastado 2px, e a borda também vai para `brand-strong`.
- Erro: `aria-invalid="true"` deixa a borda em `alert` (1.5px). A mensagem fica em `rt-field__error`, em `alert`, com o ícone triângulo e o texto, ligada por `aria-describedby`. Diz o que corrigir: "Informe um e-mail válido, como ana@exemplo.com". Nunca só a cor.
- Select: `span.rt-select` envolve o `select.rt-input` e um ícone chevron-down no lugar da seta nativa.
- Caixa de seleção: `label.rt-check` com o `input` dentro; a linha inteira tem 44px, e a caixa marcada fica em `ink`.
- Desabilitado: fundo `canvas`, borda `line`, texto `ink-muted`.
- Rótulos em sentence case, sem dois-pontos. Campo opcional diz "Opcional" na dica; os obrigatórios não levam asterisco.
- No app: fragmentos `campo`, `campoDoObjeto`, `selecao` e `caixaDeSelecao` em `templates/componentes/formulario.html`.
