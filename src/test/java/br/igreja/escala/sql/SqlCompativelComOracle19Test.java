package br.igreja.escala.sql;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.sql.VerificadorSqlOracle19.Violacao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Produção é Oracle 19c, mas os testes rodam no 23ai, que aceita sintaxe que o 19c recusa. Este teste falha se alguma
 * migração, script de infra ou bloco SQL da documentação usar essa sintaxe. Ver "Oracle 19c" no CLAUDE.md.
 */
class SqlCompativelComOracle19Test {

    private static final Path MIGRACOES = Path.of("src/main/resources/db/migration");
    private static final String MENSAGEM =
            "SQL que não roda no Oracle 19c de produção (ver \"Oracle 19c\" no CLAUDE.md;" + " falso positivo: comente "
                    + VerificadorSqlOracle19.MARCADOR + " na linha)";

    @Test
    void migracoesSaoCompativeisComOracle19() {
        assertThat(arquivos(MIGRACOES, ".sql")).isNotEmpty();
        assertThat(violacoes(arquivos(MIGRACOES, ".sql"), false)).as(MENSAGEM).isEmpty();
    }

    @Test
    void scriptsDeInfraSaoCompativeisComOracle19() {
        assertThat(violacoes(arquivos(Path.of("infra"), ".sql"), false))
                .as(MENSAGEM)
                .isEmpty();
    }

    @Test
    void sqlDaDocumentacaoECompativelComOracle19() {
        assertThat(violacoes(arquivos(Path.of("docs"), ".md"), true))
                .as(MENSAGEM)
                .isEmpty();
    }

    private static List<Violacao> violacoes(List<Path> arquivos, boolean markdown) {
        return arquivos.stream()
                .flatMap(arquivo -> {
                    String conteudo = ler(arquivo);
                    String sql = markdown ? VerificadorSqlOracle19.blocosSqlDoMarkdown(conteudo) : conteudo;
                    return VerificadorSqlOracle19.verificar(arquivo.toString(), sql).stream();
                })
                .toList();
    }

    private static List<Path> arquivos(Path raiz, String extensao) {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> caminhos = Files.walk(raiz)) {
            return caminhos.filter(p -> p.toString().endsWith(extensao))
                    .sorted()
                    .toList();
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
