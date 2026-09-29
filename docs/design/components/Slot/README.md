# Slot
Uma vaga (evento × função × posição) e seu estado: preenchida, fixada, forçada ou vazia.

- Preenchida: pílula `brand-tint` com avatar de iniciais, nome e nível em `caption`.
- `--pinned` (fixada com @PlanningPin): pílula `ink`, ícone de alfinete — o solver não mexe nela ao regerar.
- `--forced` (gerente forçou contra uma regra): `alert-tint` com contorno `alert`, ícone de alerta e a regra no meta; tooltip mostra a justificativa.
- `--empty`: contorno tracejado `alert` + "Vaga vazia". Ao clicar, mostra a regra que impediu (vinda do `SolutionManager.explain`) e os substitutos possíveis.
- Estado nunca só por cor: sempre ícone ou texto.
