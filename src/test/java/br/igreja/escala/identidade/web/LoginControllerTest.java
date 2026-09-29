package br.igreja.escala.identidade.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.identidade.config.SecurityConfig;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @Test
    void mostraOFormularioDeLoginComCsrf() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("identidade/login"))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(Matchers.containsString("name=\"email\"")))
                .andExpect(content().string(Matchers.containsString("name=\"senha\"")));
    }

    @Test
    void mostraMensagensDeErroEDeSaidaEmPortugues() throws Exception {
        mvc.perform(get("/login").param("erro", ""))
                .andExpect(content().string(Matchers.containsString("E-mail ou senha inválidos.")));
        mvc.perform(get("/login").param("saiu", ""))
                .andExpect(content().string(Matchers.containsString("Você saiu da sua conta.")));
    }

    @Test
    void usuarioJaLogadoVaiParaOInicio() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(get("/login").with(user(ana))).andExpect(redirectedUrl("/"));
    }

    @Test
    void rotaProtegidaSemLoginRedirecionaParaOLogin() throws Exception {
        mvc.perform(get("/qualquer-pagina")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginSemTokenCsrfERecusado() throws Exception {
        mvc.perform(post("/login").param("email", "ana@x.com").param("senha", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRedirecionaComAvisoDeSaida() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(post("/logout").with(user(ana)).with(csrf())).andExpect(redirectedUrl("/login?saiu"));
    }
}
