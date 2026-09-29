package br.igreja.escala;

import br.igreja.escala.identidade.config.SecurityConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de controller com MockMvc ({@code @WebMvcTest}), no perfil {@code test} e com as regras de acesso reais da
 * {@link SecurityConfig}. Todo teste de controller usa esta anotação em vez de {@code @WebMvcTest} direto.
 *
 * <p>A {@code SecurityConfig} depende do {@code UsuarioDetailsService}: declare-o com {@code @MockitoBean} na classe.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest(properties = CredenciaisDeTeste.PROPRIEDADE_CHAVE_LEMBRAR_ME)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
public @interface TesteDeController {

    /** Controllers testados. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value();
}
