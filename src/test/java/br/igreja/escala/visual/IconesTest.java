package br.igreja.escala.visual;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * O sprite de ícones só tem os nomes de src/main/frontend/icones.json (ver copiar-assets.mjs). Um nome fora da lista
 * vira um ícone em branco, sem erro nenhum; este teste pega isso nos templates.
 */
public class IconesTest {

    private static final Path LISTA = Path.of("src/main/frontend/icones.json");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** {@code icone('calendar')} ou {@code icone='calendar'} (parâmetro de outro fragmento). */
    private static final Pattern USO = Pattern.compile("icone(?:\\(|\\s*=\\s*)'([a-z0-9-]+)'");

    private static final Pattern NOME = Pattern.compile("\"([a-z0-9-]+)\"");

    /** Nomes que o sprite oferece. Use para conferir ícones que vêm do código Java. */
    public static Set<String> disponiveis() {
        return NOME.matcher(ler(LISTA)).results().map(r -> r.group(1)).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void templatesSoUsamIconesDoSprite() {
        assertThat(usadosNosTemplates()).isNotEmpty();
        assertThat(usadosNosTemplates())
                .as("ícones fora de %s; acrescente o nome lá", LISTA)
                .isSubsetOf(disponiveis());
    }

    @Test
    void reconheceOsDoisJeitosDeCitarUmIcone() {
        String template = "<svg th:replace=\"~{componentes/icone :: icone('lock')}\"></svg>"
                + "<a th:replace=\"~{componentes/botao :: botao(texto='Gerar escala', icone='sparkles')}\"></a>";

        assertThat(USO.matcher(template).results().map(r -> r.group(1))).containsExactly("lock", "sparkles");
    }

    private static Set<String> usadosNosTemplates() {
        try (Stream<Path> caminhos = Files.walk(TEMPLATES)) {
            return caminhos.filter(p -> p.toString().endsWith(".html"))
                    .flatMap(p -> USO.matcher(ler(p)).results().map(r -> r.group(1)))
                    .collect(Collectors.toCollection(TreeSet::new));
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
