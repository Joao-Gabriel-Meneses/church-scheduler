package br.igreja.escala.visual;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Nenhuma cor ou tamanho fixo nos templates: o visual vem dos componentes .rt-* e dos utilitários do Tailwind, que só
 * conhecem os tokens de docs/design/tokens.css. Este teste falha com CSS inline, bloco {@code <style>}, valor arbitrário
 * do Tailwind ({@code w-[37px]}, {@code bg-[#fff]}, {@code [mask:none]}) ou cor hexadecimal em atributo de SVG.
 * Precisou de um valor novo? Crie o token em tokens.json.
 */
class TemplatesUsamSoTokensTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern CSS_INLINE = Pattern.compile("\\s(th:)?style\\s*=|<style[\\s>]");
    private static final Pattern ATRIBUTO_DE_CLASSE =
            Pattern.compile("\\s(?:th:)?class(?:append)?\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern VALOR_ARBITRARIO = Pattern.compile("-\\[|(^|[\\s'\"])!?\\[[a-z-]+:");
    private static final Pattern COR_FIXA =
            Pattern.compile("\\s(?:fill|stroke|color|stop-color)\\s*=\\s*\"#[0-9a-fA-F]{3,8}\"");

    @Test
    void templatesUsamSoOsTokensDoDesign() {
        assertThat(templates()).isNotEmpty();
        assertThat(templates().stream().flatMap(t -> violacoes(t.toString(), ler(t)).stream()))
                .as("valor visual fora dos tokens (ver \"Visual\" no CLAUDE.md)")
                .isEmpty();
    }

    @Test
    void apontaCssInlineEBlocoDeEstilo() {
        assertThat(violacoes("a.html", "<p style=\"color:red\">\n<p th:style=\"${x}\">\n<style>p{}</style>"))
                .containsExactly("a.html:1: CSS inline", "a.html:2: CSS inline", "a.html:3: CSS inline");
    }

    @Test
    void apontaValorArbitrarioDoTailwind() {
        assertThat(violacoes("a.html", "<div class=\"p-4 w-[37px]\">\n<div th:classappend=\"${x} ? 'bg-[#fff]'\">"))
                .containsExactly("a.html:1: valor arbitrário do Tailwind", "a.html:2: valor arbitrário do Tailwind");
        assertThat(violacoes("a.html", "<div class=\"[mask-type:alpha] flex\">"))
                .containsExactly("a.html:1: valor arbitrário do Tailwind");
    }

    @Test
    void apontaCorHexadecimalEmSvg() {
        assertThat(violacoes("a.html", "<path fill=\"#1e8bff\"/>")).containsExactly("a.html:1: cor fixa");
    }

    @Test
    void aceitaTokensExpressoesEAncoras() {
        String template = """
                <div class="rt-btn rt-btn--primary w-full p-4 bg-tint-mint md:hidden">
                <a href="#fade" th:classappend="${itens[0].atual} ? 'rt-list-row--tint'">
                <svg fill="none" stroke="currentColor">
                """;

        assertThat(violacoes("a.html", template)).isEmpty();
    }

    static List<String> violacoes(String arquivo, String conteudo) {
        var violacoes = new ArrayList<String>();
        String[] linhas = conteudo.split("\n", -1);
        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i];
            String onde = arquivo + ":" + (i + 1) + ": ";
            if (CSS_INLINE.matcher(linha).find()) {
                violacoes.add(onde + "CSS inline");
            }
            var classes = ATRIBUTO_DE_CLASSE.matcher(linha);
            while (classes.find()) {
                if (VALOR_ARBITRARIO.matcher(classes.group(1)).find()) {
                    violacoes.add(onde + "valor arbitrário do Tailwind");
                }
            }
            if (COR_FIXA.matcher(linha).find()) {
                violacoes.add(onde + "cor fixa");
            }
        }
        return violacoes;
    }

    private static List<Path> templates() {
        try (Stream<Path> caminhos = Files.walk(TEMPLATES)) {
            return caminhos.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
