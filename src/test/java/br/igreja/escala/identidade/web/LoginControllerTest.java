package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeController(LoginController.class)
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
    void usaOsComponentesDoDesign() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("<body class=\"rt ")))
                .andExpect(content().string(Matchers.containsString("class=\"rt-input\" id=\"email\"")))
                .andExpect(content().string(Matchers.containsString("class=\"rt-btn rt-btn--primary w-full")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("rt-alert"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("rt-toast"))));
    }

    @Test
    void lembrarMandaOValorPadraoDoCheckboxQueORememberMeAceita() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content()
                        .string(Matchers.containsString("type=\"checkbox\" id=\"lembrar\" name=\"lembrar\">")));
    }

    @Test
    void mostraErroComoAlertaESaidaComoToastEmPortugues() throws Exception {
        mvc.perform(get("/login").param("erro", ""))
                .andExpect(content().string(Matchers.containsString("class=\"rt-alert\" role=\"alert\"")))
                .andExpect(content().string(Matchers.containsString("E-mail ou senha inválidos.")));
        mvc.perform(get("/login").param("saiu", ""))
                .andExpect(content().string(Matchers.containsString("class=\"rt-toast\"")))
                .andExpect(content().string(Matchers.containsString("Você saiu da sua conta")));
    }

    @Test
    void sessaoEncerradaExplicaOMotivo() throws Exception {
        mvc.perform(get("/login").param("expirou", ""))
                .andExpect(content().string(Matchers.containsString("class=\"rt-alert\" role=\"alert\"")))
                .andExpect(content().string(Matchers.containsString("Sua sessão foi encerrada.")))
                .andExpect(content().string(Matchers.containsString("Entre de novo.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("E-mail ou senha inválidos."))));
    }

    @Test
    void depoisDoErroMantemOEmailDigitadoESenhaVazia() throws Exception {
        when(usuarios.loadUserByUsername("ana.souza@exemplo.com"))
                .thenThrow(new UsernameNotFoundException("não existe"));
        var tentativa = mvc.perform(post("/login")
                        .param("email", "ana.souza@exemplo.com")
                        .param("senha", "errada")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?erro"))
                .andReturn();
        var sessao = (MockHttpSession) tentativa.getRequest().getSession(false);

        String html = mvc.perform(get("/login").param("erro", "").session(sessao))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html).contains("E-mail ou senha inválidos.").doesNotContain("errada");
        assertThat(campo(html, "email"))
                .contains("value=\"ana.souza@exemplo.com\"")
                .doesNotContain("autofocus");
        assertThat(campo(html, "senha")).contains("autofocus=\"autofocus\"").doesNotContain("value=");
    }

    @Test
    void semErroOFocoComecaNoEmail() throws Exception {
        String html = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        assertThat(campo(html, "email")).contains("autofocus=\"autofocus\"").doesNotContain("value=");
        assertThat(campo(html, "senha")).doesNotContain("autofocus", "value=");
    }

    @Test
    void usuarioJaLogadoVaiParaOInicio() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(get("/login").with(user(ana))).andExpect(redirectedUrl("/"));
    }

    @Test
    void fonteEIconesSaoPublicosPorqueOLoginUsa() throws Exception {
        mvc.perform(get("/fontes/urbanist/urbanist-latin-400-normal.woff2")).andExpect(status().isOk());
        mvc.perform(get("/icones/lucide.svg"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<symbol id=\"triangle-alert\"")));
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

    /** A tag {@code <input>} do campo, com os espaços reduzidos a um. */
    private static String campo(String html, String id) {
        var tag = Pattern.compile("<input[^>]*\\sid=\"" + id + "\"[^>]*>").matcher(html.replaceAll("\\s+", " "));
        assertThat(tag.find()).as("campo %s", id).isTrue();
        return tag.group();
    }
}
