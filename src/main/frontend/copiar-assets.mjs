// Hospeda a fonte e os ícones junto com o app, sem CDN. Roda antes do Tailwind no `npm run build`.
// - Urbanist (OFL-1.1): Regular 400 e Medium 500, subconjuntos latin e latin-ext, + a licença.
// - Lucide (ISC; alguns ícones derivados do Feather, MIT): sprite SVG só com os ícones de icones.json, + a licença.
// Para usar um ícone novo, acrescente o nome (https://lucide.dev/icons) em icones.json.
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const modulos = join(raiz, "node_modules");
const estaticos = join(raiz, "target", "classes", "static");

const fontes = join(estaticos, "fontes", "urbanist");
mkdirSync(fontes, { recursive: true });
for (const peso of [400, 500]) {
  for (const subconjunto of ["latin", "latin-ext"]) {
    const arquivo = `urbanist-${subconjunto}-${peso}-normal.woff2`;
    copyFileSync(join(modulos, "@fontsource", "urbanist", "files", arquivo), join(fontes, arquivo));
  }
}
copyFileSync(join(modulos, "@fontsource", "urbanist", "LICENSE"), join(fontes, "OFL.txt"));

const lucide = join(modulos, "lucide-static");
const versao = JSON.parse(readFileSync(join(lucide, "package.json"), "utf8")).version;
const nomes = JSON.parse(readFileSync(join(raiz, "src", "main", "frontend", "icones.json"), "utf8"));
const simbolos = nomes.map((nome) => {
  const arquivo = join(lucide, "icons", `${nome}.svg`);
  if (!existsSync(arquivo)) {
    throw new Error(`Ícone "${nome}" de icones.json não existe no lucide-static ${versao}.`);
  }
  // Fica só o desenho: traço, cor e espessura (1.5) vêm do <svg> que usa o símbolo.
  const desenho = readFileSync(arquivo, "utf8")
    .replace(/^[\s\S]*?<svg[^>]*>/, "")
    .replace(/<\/svg>\s*$/, "")
    .replace(/\s*\n\s*/g, "");
  return `<symbol id="${nome}" viewBox="0 0 24 24">${desenho}</symbol>`;
});

const icones = join(estaticos, "icones");
mkdirSync(icones, { recursive: true });
writeFileSync(
  join(icones, "lucide.svg"),
  `<!-- Lucide ${versao} (ISC; alguns ícones derivados do Feather, MIT). Licença: LICENSE.txt -->\n` +
    `<svg xmlns="http://www.w3.org/2000/svg">\n${simbolos.join("\n")}\n</svg>\n`,
);
copyFileSync(join(lucide, "LICENSE"), join(icones, "LICENSE.txt"));

console.log(`Fonte Urbanist e ${nomes.length} ícones Lucide ${versao} copiados para ${estaticos}`);
