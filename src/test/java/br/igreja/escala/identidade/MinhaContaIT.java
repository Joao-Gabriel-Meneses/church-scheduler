package br.igreja.escala.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Minha conta, ponta a ponta: a pessoa muda o próprio nome e o e-mail, que é o login. */
@TesteDeIntegracao
@Transactional
class MinhaContaIT {

    private static final String SENHA = "senha-da-ana";

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void cadastraAAnaEABia() {
        usuarios.save(Usuario.membro("Ana Conta", "ana.minhaconta@teste.local", passwordEncoder.encode(SENHA)));
        usuarios.save(Usuario.membro("Bia Conta", "bia.minhaconta@teste.local", "{noop}x"));
    }

    @Test
    void mudaONomeEOEmailEEntraComONovo() throws Exception {
        var sessao = entrar("ana.minhaconta@teste.local");

        mvc.perform(post("/conta")
                        .session(sessao)
                        .with(csrf())
                        .param("nome", "Ana Souza")
                        .param("email", "Ana.Souza.MinhaConta@teste.local")
                        .param("telefone", "(11) 98888-7777"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Olá, <span>Ana Souza</span>!")));
        entityManager.flush();
        entityManager.clear();
        var conta = usuarios.findByEmail("ana.souza.minhaconta@teste.local").orElseThrow();
        assertThat(conta.getNome()).isEqualTo("Ana Souza");
        assertThat(conta.getTelefone()).isEqualTo("(11) 98888-7777");
        mvc.perform(login("ana.souza.minhaconta@teste.local")).andExpect(authenticated());
        mvc.perform(login("ana.minhaconta@teste.local")).andExpect(unauthenticated());
    }

    @Test
    void naoPegaOEmailDeOutraConta() throws Exception {
        var sessao = entrar("ana.minhaconta@teste.local");

        mvc.perform(post("/conta")
                        .session(sessao)
                        .with(csrf())
                        .param("nome", "Ana Conta")
                        .param("email", "BIA.minhaconta@teste.local"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(Matchers.containsString(
                                "O e-mail bia.minhaconta@teste.local já é o login de outra conta.")));

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findByEmail("ana.minhaconta@teste.local")).isPresent();
    }

    private MockHttpSession entrar(String email) throws Exception {
        var resultado = mvc.perform(login(email)).andExpect(authenticated()).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private static RequestBuilder login(String email) {
        return formLogin("/login")
                .userParameter("email")
                .passwordParam("senha")
                .user(email)
                .password(SENHA);
    }
}
