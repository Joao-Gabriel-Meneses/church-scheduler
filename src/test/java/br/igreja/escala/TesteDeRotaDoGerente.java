package br.igreja.escala;

import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.AcessoAoMinisterio;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@link TesteDeController} para rotas de {@code /ministerios/{ministerioId}}: a autorização é a de verdade
 * ({@link AcessoAoMinisterio}), sobre um {@link MembresiaRepository} simulado. No {@code @BeforeEach}, chame
 * {@link AcessoDeTeste#configurar(MembresiaRepository)} com o repositório injetado.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TesteDeController({})
@Import(AcessoAoMinisterio.class)
@MockitoBean(types = {MembresiaRepository.class, UsuarioDetailsService.class})
public @interface TesteDeRotaDoGerente {

    /** Controllers testados. */
    @AliasFor(annotation = TesteDeController.class, attribute = "value")
    Class<?>[] value();
}
