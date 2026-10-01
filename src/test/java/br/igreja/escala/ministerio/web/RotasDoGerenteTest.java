package br.igreja.escala.ministerio.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Toda rota de {@code /ministerios/{ministerioId}} é do gerente daquele ministério: tem {@link GerenteDoMinisterio}
 * no método ou na classe. Esquecer a anotação abriria a página para qualquer membro logado, sem erro nenhum.
 */
class RotasDoGerenteTest {

    static final String PREFIXO = "/ministerios/{ministerioId}";

    @Test
    void todaRotaDeUmMinisterioExigeOGerenteDele() {
        assertThat(rotasSemProtecao(controllers()))
                .as("anote com @GerenteDoMinisterio")
                .isEmpty();
    }

    @Test
    void apontaRotaDoMinisterioSemAnotacaoNaClasseNemNoMetodo() {
        assertThat(rotasSemProtecao(List.of(Exemplos.class, ExemplosProtegidos.class, ForaDoMinisterio.class)))
                .containsExactly("Exemplos.semAnotacao " + PREFIXO + "/membros");
    }

    static List<String> rotasSemProtecao(List<Class<?>> controllers) {
        var semProtecao = new ArrayList<String>();
        for (Class<?> controller : controllers) {
            boolean classeProtegida = AnnotatedElementUtils.hasAnnotation(controller, GerenteDoMinisterio.class);
            for (Method metodo : controller.getDeclaredMethods()) {
                for (String caminho : caminhos(controller, metodo)) {
                    boolean protegido =
                            classeProtegida || AnnotatedElementUtils.hasAnnotation(metodo, GerenteDoMinisterio.class);
                    if (caminho.startsWith(PREFIXO) && !protegido) {
                        semProtecao.add(controller.getSimpleName() + "." + metodo.getName() + " " + caminho);
                    }
                }
            }
        }
        return semProtecao;
    }

    /** Caminhos completos (classe + método) de um método de controller; vazio se não for rota. */
    private static List<String> caminhos(Class<?> controller, Method metodo) {
        var doMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
        if (doMetodo == null) {
            return List.of();
        }
        var daClasse = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        List<String> bases = daClasse == null || daClasse.path().length == 0 ? List.of("") : List.of(daClasse.path());
        List<String> finais = doMetodo.path().length == 0 ? List.of("") : List.of(doMetodo.path());
        return bases.stream()
                .flatMap(base -> finais.stream().map(fim -> base + fim))
                .toList();
    }

    /** Controllers da aplicação, sem os de exemplo declarados dentro de classes de teste. */
    private static List<Class<?>> controllers() {
        var varredura = new ClassPathScanningCandidateComponentProvider(false);
        varredura.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        return varredura.findCandidateComponents("br.igreja.escala").stream()
                .map(definicao -> ClassUtils.resolveClassName(definicao.getBeanClassName(), null))
                .filter(classe -> !classe.getName().contains("Test$"))
                .<Class<?>>map(classe -> classe)
                .toList();
    }

    @RequestMapping(PREFIXO)
    static class Exemplos {

        @GetMapping("/membros")
        void semAnotacao() {}

        @GerenteDoMinisterio
        @GetMapping("/funcoes")
        void comAnotacao() {}

        void naoERota() {}
    }

    @GerenteDoMinisterio
    @RequestMapping(PREFIXO + "/eventos")
    static class ExemplosProtegidos {

        @GetMapping
        void lista() {}
    }

    @RequestMapping("/ministerios")
    static class ForaDoMinisterio {

        @GetMapping
        void listaDoAdmin() {}
    }
}
