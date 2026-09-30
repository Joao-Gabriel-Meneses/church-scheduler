package br.igreja.escala;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Uma ação primária por tela (docs/design/components/Button): toda página HTML renderizada nos testes tem no máximo um
 * {@code .rt-btn--primary}. Vale em todo {@link TesteDeController} e {@link TesteDeIntegracao} (ver
 * {@link GuardasDeTela}). As vitrines de componentes, que mostram as variantes lado a lado, ficam de fora.
 */
public final class UmPrimarioPorTela implements ResultMatcher {

    /** Views que são catálogos de componentes, não telas. */
    static final Set<String> VITRINES = Set.of("dev/componentes", "teste/fragmentos");

    private static final Pattern PRIMARIO = Pattern.compile("\\sclass=\"(?:[^\"]*\\s)?rt-btn--primary(?=[\\s\"])");

    @Override
    public void match(MvcResult resultado) throws Exception {
        var visao = resultado.getModelAndView();
        String tipo = resultado.getResponse().getContentType();
        if (visao == null || tipo == null || !tipo.startsWith("text/html") || VITRINES.contains(visao.getViewName())) {
            return;
        }
        long primarios = contar(resultado.getResponse().getContentAsString());
        if (primarios > 1) {
            throw new AssertionError("A tela " + visao.getViewName() + " tem " + primarios
                    + " botões primários; o design pede um por tela. Deixe a ação que faz o trabalho como primary"
                    + " e as outras como secondary, dark ou outline.");
        }
    }

    static long contar(String html) {
        return PRIMARIO.matcher(html).results().count();
    }
}
