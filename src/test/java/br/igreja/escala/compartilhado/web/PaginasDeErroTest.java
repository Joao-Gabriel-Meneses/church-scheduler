package br.igreja.escala.compartilhado.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.Pessoas;
import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** As páginas de erro renderizam no layout simples, com o texto em português e um caminho de volta. */
@TesteDeController(PaginasDeErroTest.Paginas.class)
@Import(PaginasDeErroTest.Paginas.class)
class PaginasDeErroTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @Test
    void acessoNegado() throws Exception {
        renderiza("403", "Você não tem acesso a esta página");
    }

    @Test
    void naoEncontrado() throws Exception {
        renderiza("404", "Não encontramos esta página");
    }

    @Test
    void outroErro() throws Exception {
        renderiza("outro", "Algo deu errado aqui");
    }

    private void renderiza(String qual, String titulo) throws Exception {
        mvc.perform(get("/teste-erro/" + qual).with(user(Pessoas.membro(1L, "Ana"))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(titulo)))
                .andExpect(content().string(Matchers.containsString("href=\"/\"")))
                .andExpect(content().string(Matchers.containsString("<body class=\"rt ")));
    }

    @Controller
    static class Paginas {

        @GetMapping("/teste-erro/{qual}")
        String erro(@PathVariable String qual) {
            return qual.equals("outro") ? "error" : "error/" + qual;
        }
    }
}
