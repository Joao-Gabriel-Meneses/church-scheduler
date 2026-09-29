package br.igreja.escala.identidade;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Login ponta a ponta no Oracle: mapeamento JPA, BCrypt e Spring Security. Cria o próprio usuário. */
@TesteDeIntegracao
@Transactional
class LoginIT {

    private static final String EMAIL = "membro.login@teste.local";
    private static final String SENHA = "senha-do-membro";

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void criaMembro() {
        usuarios.save(Usuario.membro("Membro do Teste", EMAIL, passwordEncoder.encode(SENHA)));
    }

    @Test
    void entraComASenhaCorreta() throws Exception {
        mvc.perform(login(EMAIL, SENHA))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("MEMBRO"));
    }

    @Test
    void entraComEmailEmMaiusculas() throws Exception {
        mvc.perform(login("Membro.Login@Teste.LOCAL", SENHA)).andExpect(authenticated());
    }

    @Test
    void recusaSenhaErrada() throws Exception {
        mvc.perform(login(EMAIL, "errada"))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());
    }

    @Test
    void recusaUsuarioInexistente() throws Exception {
        mvc.perform(login("ninguem@teste.local", SENHA))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());
    }

    @Test
    void healthEPublicoEConsultaOBanco() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static RequestBuilder login(String email, String senha) {
        return formLogin("/login")
                .userParameter("email")
                .passwordParam("senha")
                .user(email)
                .password(senha);
    }
}
