package br.igreja.escala.identidade;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Primeiro acesso com senha provisória, ponta a ponta: login, troca obrigatória e a sessão atualizada. */
@TesteDeIntegracao
@Transactional
class SenhaProvisoriaIT {

    private static final String EMAIL = "provisoria@teste.local";

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cadastraComSenhaProvisoria() {
        usuarios.save(Usuario.comSenhaProvisoria(
                "Carla Dias", EMAIL, "(11) 90000-0000", passwordEncoder.encode("provisoria-123")));
    }

    @Test
    void soAbreATrocaDeSenhaAteTrocarEDepoisAbreTudo() throws Exception {
        var sessao = entrar("provisoria-123");

        mvc.perform(get("/").session(sessao)).andExpect(redirectedUrl("/conta/senha"));
        mvc.perform(get("/conta/senha").session(sessao)).andExpect(status().isOk());

        mvc.perform(post("/conta/senha")
                        .session(sessao)
                        .with(csrf())
                        .param("novaSenha", "senha-da-carla")
                        .param("confirmacao", "senha-da-carla"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(sessao)).andExpect(status().isOk());
        mvc.perform(login("senha-da-carla")).andExpect(authenticated());
        mvc.perform(login("provisoria-123")).andExpect(unauthenticated());
    }

    private MockHttpSession entrar(String senha) throws Exception {
        var resultado = mvc.perform(login(senha)).andExpect(authenticated()).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private static RequestBuilder login(String senha) {
        return formLogin("/login")
                .userParameter("email")
                .passwordParam("senha")
                .user(EMAIL)
                .password(senha);
    }
}
