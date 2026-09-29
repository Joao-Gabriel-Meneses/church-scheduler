# Toast
Confirmação curta de que uma ação deu certo: "Função Projeção criada", "Disponibilidade de outubro salva". Não veio do Claude Design; foi criado no app seguindo os tokens e fica em `toast.css`.

- Pílula em `ink` com texto `on-ink`, ícone de círculo com check e botão circular de fechar (`aria-label="Fechar aviso"`). É flutuante, então usa `shadow-float`.
- Só para sucesso. O que precisa de ação do gerente vai em `AlertBanner`; erro de formulário fica no próprio campo (`Field`).
- Texto no passado, em sentence case e sem ponto final: diz o que foi feito e com o quê.
- `role="status"` dentro de uma região `aria-live="polite"`. Some sozinho em 5 s, mas não enquanto o ponteiro ou o foco estiverem nele. Com `prefers-reduced-motion`, sai sem transição.
- Posição: no celular, centralizado logo acima da barra de navegação inferior; no desktop, no canto inferior direito.
- No app: fragmento `toast` em `templates/componentes/toast.html`. Depois de um redirect, use o flash attribute `sucesso`; numa resposta htmx, mande o fragmento com `hx-swap-oob="beforeend:#toasts"`.
