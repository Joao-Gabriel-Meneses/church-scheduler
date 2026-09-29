package br.igreja.escala.compartilhado.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * A vitrine só existe no perfil dev. Aqui, no perfil test, a rota não existe; as páginas são renderizadas por um
 * controller de teste que reaproveita o de dev, para um template quebrado não passar despercebido.
 */
@TesteDeController(ComponentesDevControllerTest.Vitrine.class)
@Import(ComponentesDevControllerTest.Vitrine.class)
class ComponentesDevControllerTest {

    private static final UsuarioAutenticado ANA = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @Test
    void naoExisteForaDoPerfilDev() throws Exception {
        mvc.perform(get("/dev/componentes").with(user(ANA))).andExpect(status().isNotFound());
        mvc.perform(get("/dev/componentes/gerente").with(user(ANA))).andExpect(status().isNotFound());
    }

    @Test
    void vitrineMostraUmaSecaoPorComponente() throws Exception {
        mvc.perform(get("/teste/vitrine").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(Matchers.stringContainsInOrder(
                                "id=\"button\"",
                                "id=\"icon-button\"",
                                "id=\"badge\"",
                                "id=\"list-row\"",
                                "id=\"data-table\"",
                                "id=\"alert-banner\"",
                                "id=\"field\"",
                                "id=\"toast\"",
                                "id=\"side-rail\"",
                                "id=\"nav-pills\"",
                                "id=\"toolbar\"")));
    }

    @Test
    void exemploDoMembroTemBarraInferiorEToast() throws Exception {
        mvc.perform(get("/teste/vitrine/membro").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Mídia — Outubro")))
                .andExpect(content().string(Matchers.containsString("rt-nav border-t-2")))
                .andExpect(content().string(Matchers.containsString("Disponibilidade de outubro salva")));
    }

    @Test
    void exemploDoGerenteTemRailNavPillsEToolbar() throws Exception {
        mvc.perform(get("/teste/vitrine/gerente").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("class=\"rt-rail\"")))
                .andExpect(content().string(Matchers.containsString("class=\"rt-nav flex-wrap\"")))
                .andExpect(content().string(Matchers.containsString("id=\"barra\" class=\"rt-toolbar\"")))
                .andExpect(content().string(Matchers.containsString("class=\"rt-table\"")));
    }

    @Controller
    static class Vitrine {

        private final ComponentesDevController dev = new ComponentesDevController();

        @GetMapping("/teste/vitrine")
        String componentes(Model model) {
            return dev.componentes(model);
        }

        @GetMapping("/teste/vitrine/membro")
        String membro(Model model) {
            return dev.membro(model);
        }

        @GetMapping("/teste/vitrine/gerente")
        String gerente(Model model) {
            return dev.gerente(model);
        }
    }
}
