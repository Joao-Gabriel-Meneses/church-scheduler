package br.igreja.escala.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TestcontainersConfiguration;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Login ponta a ponta no Oracle: Flyway, mapeamento JPA, BCrypt e admin inicial. */
@SpringBootTest(
        properties = {
            "escala.admin.nome=Admin do Teste",
            "escala.admin.email=Admin.Teste@Escala.local",
            "escala.admin.senha=senha-do-teste"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Test
    void criaOAdminInicialComSenhaCriptografada() {
        var admin = usuarios.findByEmail("admin.teste@escala.local").orElseThrow();

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.getNome()).isEqualTo("Admin do Teste");
        assertThat(admin.getSenhaHash()).startsWith("{bcrypt}");
    }

    @Test
    void entraComASenhaCorreta() throws Exception {
        mvc.perform(formLogin("/login")
                        .userParameter("email")
                        .passwordParam("senha")
                        .user("admin.teste@escala.local")
                        .password("senha-do-teste"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("MEMBRO", "ADMIN"));
    }

    @Test
    void recusaSenhaErrada() throws Exception {
        mvc.perform(formLogin("/login")
                        .userParameter("email")
                        .passwordParam("senha")
                        .user("admin.teste@escala.local")
                        .password("errada"))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());
    }

    @Test
    void healthEPublicoEConsultaOBanco() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
