"""Regenera tokens.css a partir de tokens.json. Uso: python3 docs/design/gerar_tokens_css.py"""
import json
from pathlib import Path

aqui = Path(__file__).parent
t = json.loads((aqui / "tokens.json").read_text(encoding="utf-8"))
linhas = ['/* GERADO a partir de tokens.json (design system "Escala" no Claude Design). '
          'Não edite à mão: altere tokens.json e rode gerar_tokens_css.py. */', ":root{"]
for c in t["color"]["tokens"]:
    v = c["value"] if isinstance(c["value"], str) else c["value"]["light"]
    linhas.append(f"  --{c['name']}: {v};")
for fam, v in t["type"]["families"].items():
    linhas.append(f"  --font-{fam}: {v};")
for g in t["type"]["groups"]:
    for s in g["styles"]:
        n = s["name"]
        extra = f" --text-{n}--letter-spacing: {s['letterSpacing']};" if "letterSpacing" in s else ""
        linhas.append(f"  --text-{n}: {s['fontSize']}; --text-{n}--line-height: {s['lineHeight']};"
                      f" --text-{n}--font-weight: {s['fontWeight']};{extra}")
for fam in ("spacing", "radius", "size", "shadow"):
    for x in t.get(fam, {}).get("tokens", []):
        linhas.append(f"  --{x['name']}: {x['value']};")
linhas.append("}")
(aqui / "tokens.css").write_text("\n".join(linhas) + "\n", encoding="utf-8")
print("tokens.css atualizado")
