package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * O perfil prod sem subir o Spring: o {@code application-prod.properties} não guarda segredo, e o conjunto do
 * {@code application.yaml} com ele por cima tem o que a produção atrás do Cloudflare Tunnel precisa.
 */
class ConfiguracaoDeProducaoTest {

    private static final Pattern CHAVE_DE_SEGREDO =
            Pattern.compile("(?i).*(senha|password|pwd|secret|segredo|token|chave|key|credential|credencial).*");

    /** Só a referência a uma variável de ambiente, no máximo com padrão vazio. */
    private static final Pattern SO_VARIAVEL = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*:?}");

    private static final Pattern URL_COM_CREDENCIAL = Pattern.compile("(?i)[a-z][a-z0-9+.-]*://[^/\\s:@]+:[^/\\s@]+@");

    private static Properties producao;
    private static StandardEnvironment ambiente;

    @BeforeAll
    static void carregar() throws IOException {
        producao = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

        // Mesma precedência do Spring: o arquivo do perfil vence o application.yaml.
        ambiente = new StandardEnvironment();
        var fontes = ambiente.getPropertySources();
        fontes.addLast(new PropertiesPropertySource("application-prod.properties", producao));
        new YamlPropertySourceLoader()
                .load("application.yaml", new ClassPathResource("application.yaml"))
                .forEach(fontes::addLast);
    }

    @Test
    void segredoSoVemDeVariavelDeAmbiente() {
        assertThat(producao.stringPropertyNames())
                .filteredOn(chave -> CHAVE_DE_SEGREDO.matcher(chave).matches())
                .isNotEmpty()
                .allSatisfy(chave ->
                        assertThat(producao.getProperty(chave)).as(chave).matches(SO_VARIAVEL));
    }

    @Test
    void nenhumValorTrazUsuarioESenhaNaUrl() {
        assertThat(producao.stringPropertyNames())
                .allSatisfy(chave ->
                        assertThat(producao.getProperty(chave)).as(chave).doesNotContainPattern(URL_COM_CREDENCIAL));
    }

    @Test
    void oBancoVemTodoDoAmbiente() {
        assertThat(producao)
                .containsEntry("spring.datasource.url", "${DB_URL}")
                .containsEntry("spring.datasource.username", "${DB_USUARIO}")
                .containsEntry("spring.datasource.password", "${DB_SENHA}");
    }

    /** O cloudflared fala HTTP com o app e manda X-Forwarded-Proto: https. */
    @Test
    void confiaNosCabecalhosDoProxy() {
        assertThat(ambiente.getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }

    @Test
    void cookieDeSessaoSecureHttpOnlyESameSiteLax() {
        assertThat(ambiente.getProperty("server.servlet.session.cookie.secure", Boolean.class))
                .isTrue();
        assertThat(ambiente.getProperty("server.servlet.session.cookie.http-only", Boolean.class))
                .isTrue();
        assertThat(ambiente.getProperty("server.servlet.session.cookie.same-site"))
                .isEqualToIgnoringCase("lax");
    }

    /** O nome do arquivo muda junto com o conteúdo, e o cache do Cloudflare nunca serve CSS velho. */
    @Test
    void recursosEstaticosComHashDoConteudo() {
        assertThat(ambiente.getProperty("spring.web.resources.chain.strategy.content.enabled", Boolean.class))
                .isTrue();
        assertThat(ambiente.getProperty("spring.web.resources.chain.strategy.content.paths"))
                .isEqualTo("/**");
    }

    /** O Autonomous DB Always Free aceita 30 sessões. */
    @Test
    void poolDeNoMaximoDezConexoes() {
        assertThat(ambiente.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                .isBetween(1, 10);
    }

    @Test
    void dialetoDoOracle19() {
        assertThat(ambiente.getProperty(
                        "spring.jpa.properties.jakarta.persistence.database-major-version", Integer.class))
                .isEqualTo(19);
    }

    @Test
    void actuatorSoComHealthEInfoESemDetalhesParaQuemNaoEstaLogado() {
        assertThat(ambiente.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
        assertThat(ambiente.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("when-authorized");
        assertThat(ambiente.getProperty("management.endpoint.health.roles")).isEqualTo("ADMIN");
    }

    @Test
    void logsEmJsonNoStdout() {
        assertThat(ambiente.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }
}
