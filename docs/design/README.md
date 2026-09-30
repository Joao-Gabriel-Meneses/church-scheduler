Escala é o sistema visual do app de escalas dos ministérios da igreja: fundo cinza-névoa, superfícies brancas, tinta grafite e um único azul que marca o que importa. Tudo é arredondado — pílulas, círculos e painéis de canto largo — e quase nada tem sombra. É mobile-first: o membro marca o mês inteiro no celular em menos de um minuto; o gerente monta e publica no desktop.

## Conteúdo e voz

- Português do Brasil, tom acolhedor e direto, tratando por "você": "Marque os dias em que você pode servir".
- Sentence case em tudo. Títulos de página no padrão **Ministério — Período** (`title`): "Mídia — Outubro".
- Vocabulário fixo: *escala*, *evento*, *função*, *nível*, *vaga*, *disponibilidade*, *travar*, *publicar*, *desistir*, *troca*. Opções de disponibilidade: "Pode", "Prefiro não, mas posso", "Não pode".
- Datas como "12/10 · Dom" e horários "18h00"; fuso America/Sao_Paulo. Metadados separados por " · ".
- Todo alerta diz o quê, onde e por quê — nome da regra incluído: "12/10 · Culto de domingo — Transmissão sem ninguém. Regra: LIMITE_POR_PERIODO".
- Botões com verbo no infinitivo: "Gerar escala", "Publicar", "Pedir troca". Sem emoji.

## Cor

- Página em `canvas`; painéis em `canvas` com borda de 2px `surface`. Separação por borda branca, não sombra.
- `ink` = estado ativo e decisão: nav atual, "Pode" marcado, vaga fixada, botão "Publicar", período travado.
- `brand` é raro: botão primário (`brand-strong` quando há texto pequeno), ponto de "Publicada", dias com evento no calendário.
- `brand-tint` = pessoa escalada (Slot) e seleção. `alert`/`alert-tint` = o que precisa do gerente: vaga vazia, alocação forçada, sem resposta, falha de e-mail.
- `tint-mint`, `tint-rose`, `tint-lemon` identificam ministérios em etiquetas (Mídia, Louvor, Recepção).
- Contraste: `ink`, `ink-muted` e `alert` passam 4.5:1 em `surface`, `canvas` e nos tints. Branco sobre `brand` só em texto ≥24px. `ink-subtle` é decorativo.
- Estado nunca só por cor: vaga vazia tem tracejado + ícone + texto; forçada tem ícone + regra.
- Foco: anel sólido de 2px em `brand-strong`, afastado 2px.

## Tipografia

- Uma família, `sans` (Urbanist como substituta livre da Lufga), em Regular 400 e Medium 500 — nunca bold.
- Escala: `display` 56, `title` 28, `stat` 36, `heading` 20, `body-lg` 16, `body` 14, `label` 12, `caption` 10.

## Forma, espaço e layout

- `radius-pill` para todo controle e para as vagas (Slot); `radius-lg` para painéis; `radius-md` para linhas; `radius-sm` para tiles de ícone.
- Alvo de toque mínimo `size-control` (44px) no celular; `size-control-lg` na toolbar do desktop.
- **Membro (celular):** título do mês, `AvailabilityPicker`, "Minhas escalas" em `ListRow`, pedir troca em sheet inferior com `shadow-float`.
- **Gerente (desktop):** `SideRail` de ministérios, `NavPills` no topo, `Toolbar` de período, `AlertBanner`s, linha de `StatCard`s e `ScheduleGrid`.
- Estados da escala com `Badge`: Rascunho → Publicada; trava com `Badge --locked`.
- Movimento: transições de cor de 150ms; a geração (até 30s) mostra progresso na própria Toolbar, sem tela de carregamento cheia.

## Iconografia

- Ícones de traço de 1.5px, cantos arredondados, 20px em controles e 16px em pílulas, em `currentColor`.
- Significados fixos: ✓ pode, ✕ não pode, faísca = gerar/"prefiro não", alfinete = fixada, triângulo = alerta, cadeado = travado, setas cruzadas = troca, monitor = Projeção, câmera = Transmissão.
- Os previews usam desenhos próprios. Em produção, use **Lucide** com `stroke-width="1.5"` (substituição sinalizada).
