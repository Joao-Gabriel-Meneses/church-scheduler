package br.igreja.escala;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de integração (*IT) no Oracle do Testcontainers, com toda a configuração declarada aqui.
 *
 * <p>Todos os *IT usam exatamente esta anotação, então compartilham um único contexto Spring. Regras:
 *
 * <ul>
 *   <li>O banco começa vazio a cada execução (container novo, sem reuse).
 *   <li>Cada teste cria os dados de que precisa e não depende de dados de outra classe nem do admin criado na
 *       subida. Use {@code @Transactional} na classe de teste para desfazer tudo ao final.
 *   <li>As classes rodam em ordem aleatória (Failsafe {@code runOrder=random}).
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties = {
            "escala.admin.nome=" + CredenciaisDeTeste.ADMIN_NOME,
            "escala.admin.email=" + CredenciaisDeTeste.ADMIN_EMAIL,
            "escala.admin.senha=" + CredenciaisDeTeste.ADMIN_SENHA,
            CredenciaisDeTeste.PROPRIEDADE_CHAVE_LEMBRAR_ME
        })
public @interface TesteDeIntegracao {}
