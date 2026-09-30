# Toolbar
Barra do topo da tela de escala: seletor de período escuro, estado da trava, atalho para regras e a ação primária.

- Tudo em `size-control-lg`, separado por `space-2`, acima da grade.
- `rt-seg` com setas e o mês por extenso ("Outubro 2026").
- A trava aparece como badge `--locked` quando ativa; clicar abre a confirmação para destravar.
- Ação primária muda com o estado: "Gerar escala" (sem rascunho) → "Regerar não fixadas" (com rascunho).
- Contextual: cada página passa só o que faz sentido nela. "Gerar escala" só é primário na página de escalas (um primário por tela).
- No celular (< 560px), uma linha só: seletor de período compacto ("Out 2026", 44px) e um IconButton "Mais ações" que abre um `Sheet` com a trava como Badge ("Disponibilidade travada" ou "Disponibilidade aberta"), Regras e a ação da página. Regras de layout em `toolbar-celular.css`.
- No app: fragmento `toolbar` em `templates/componentes/toolbar.html`.
