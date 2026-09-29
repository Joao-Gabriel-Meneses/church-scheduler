package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Todo teste que sobe o Spring declara o perfil {@code test}, em geral via {@link TesteDeIntegracao} ou
 * {@link TesteDeController}. Sem isso ele roda no perfil padrão, e um {@code SPRING_PROFILES_ACTIVE=dev} no ambiente
 * traria o seed de dev para dentro dele. O {@code EscalaApplicationIT} confere que o perfil declarado vence o ambiente.
 */
class PerfilDosTestesTest {

    @Test
    void todoTesteQueSobeOSpringRodaSoNoPerfilTest() {
        var testesComSpring = classesDeTesteQueSobemOSpring();

        assertThat(testesComSpring)
                .as("a varredura precisa enxergar @TesteDeIntegracao e @TesteDeController")
                .extracting(Class::getSimpleName)
                .contains("EscalaApplicationIT", "LoginControllerTest");
        assertThat(testesComSpring)
                .filteredOn(classe -> !rodaSoNoPerfilTest(classe))
                .as("testes sem @ActiveProfiles(\"test\"); use @TesteDeIntegracao ou @TesteDeController")
                .isEmpty();
    }

    /** {@code @SpringBootTest} e as fatias ({@code @WebMvcTest}...) usam {@code @BootstrapWith}. */
    private static List<Class<?>> classesDeTesteQueSobemOSpring() {
        var varredura = new ClassPathScanningCandidateComponentProvider(false);
        varredura.addIncludeFilter(new AnnotationTypeFilter(BootstrapWith.class));
        varredura.addIncludeFilter(new AnnotationTypeFilter(ContextConfiguration.class));
        return varredura.findCandidateComponents(PerfilDosTestesTest.class.getPackageName()).stream()
                .<Class<?>>map(definicao -> ClassUtils.resolveClassName(definicao.getBeanClassName(), null))
                .toList();
    }

    private static boolean rodaSoNoPerfilTest(Class<?> classe) {
        var perfis = AnnotatedElementUtils.findMergedAnnotation(classe, ActiveProfiles.class);
        return perfis != null && List.of(perfis.profiles()).equals(List.of("test"));
    }
}
