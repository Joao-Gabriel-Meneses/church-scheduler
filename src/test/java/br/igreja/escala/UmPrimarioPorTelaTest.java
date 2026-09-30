package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A guarda vale em todo teste de controller. As vitrines (com vários primários de propósito) passam por ela no
 * ComponentesTest e no ComponentesDevControllerTest.
 */
@TesteDeController(UmPrimarioPorTelaTest.Telas.class)
@Import(UmPrimarioPorTelaTest.Telas.class)
class UmPrimarioPorTelaTest {

    private static final UsuarioAutenticado ANA = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @Test
    void contaSoOsBotoesPrimariosNoAtributoClass() {
        String html = "<a class=\"rt-btn rt-btn--primary w-full\">Salvar</a>"
                + "<button class=\"rt-btn rt-btn--secondary\">Cancelar</button>"
                + "<!-- rt-btn--primary em comentário não conta -->"
                + "<span class=\"rt-btn--primary-x\"></span>";

        assertThat(UmPrimarioPorTela.contar(html)).isEqualTo(1);
    }

    @Test
    void recusaTelaComDoisPrimarios() {
        assertThatThrownBy(() -> mvc.perform(get("/teste/dois-primarios").with(user(ANA))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("teste/dois-primarios tem 2 botões primários");
    }

    @Controller
    static class Telas {

        @GetMapping("/teste/dois-primarios")
        String doisPrimarios() {
            return "teste/dois-primarios";
        }
    }
}
