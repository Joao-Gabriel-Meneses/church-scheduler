package br.igreja.escala;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de integração (*IT) no Oracle do Testcontainers, com toda a configuração declarada aqui.
 *
 * <p>Todos os *IT usam exatamente esta anotação, então compartilham um único contexto Spring. Regras:
 *
 * <ul>
 *   <li>Roda no perfil {@code test}, nunca no {@code dev}: {@code @ActiveProfiles} vence o
 *       {@code SPRING_PROFILES_ACTIVE} do ambiente.
 *   <li>O banco começa vazio a cada execução (container novo, sem reuse).
 *   <li>Cada teste cria os dados de que precisa e não depende de dados de outra classe nem do admin criado na
 *       subida. Use {@code @Transactional} na classe de teste para desfazer tudo ao final.
 *   <li>As classes rodam em ordem aleatória (Failsafe {@code runOrder=random}).
 *   <li>Toda página renderizada passa pelas {@link GuardasDeTela} (ex.: um botão primário por tela).
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GuardasDeTela.class})
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "escala.admin.nome=" + CredenciaisDeTeste.ADMIN_NOME,
            "escala.admin.email=" + CredenciaisDeTeste.ADMIN_EMAIL,
            "escala.admin.senha=" + CredenciaisDeTeste.ADMIN_SENHA,
            "escala.url-base=" + CredenciaisDeTeste.URL_BASE,
            CredenciaisDeTeste.PROPRIEDADE_CHAVE_LEMBRAR_ME
        })
public @interface TesteDeIntegracao {}
