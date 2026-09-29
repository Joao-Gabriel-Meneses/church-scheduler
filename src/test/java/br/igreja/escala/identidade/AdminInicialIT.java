package br.igreja.escala.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import br.igreja.escala.CredenciaisDeTeste;
import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.identidade.service.AdminInicialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercita o seed do admin num banco sem usuários. Não depende do admin criado na subida do contexto: apaga a tabela
 * dentro da transação do teste, que é desfeita ao final.
 */
@TesteDeIntegracao
@Transactional
class AdminInicialIT {

    @Autowired
    AdminInicialService adminInicial;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void zeraUsuarios() {
        usuarios.deleteAllInBatch();
    }

    @Test
    void criaOAdminDeclaradoNaConfiguracaoComSenhaCriptografada() throws Exception {
        adminInicial.run(new DefaultApplicationArguments());
        usuarios.flush();

        var admin = usuarios.findByEmail(CredenciaisDeTeste.ADMIN_EMAIL).orElseThrow();
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.getNome()).isEqualTo(CredenciaisDeTeste.ADMIN_NOME);
        assertThat(admin.getSenhaHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(CredenciaisDeTeste.ADMIN_SENHA, admin.getSenhaHash()))
                .isTrue();
    }

    @Test
    void adminCriadoConsegueEntrarComoAdmin() throws Exception {
        adminInicial.criarSeNecessario();

        mvc.perform(formLogin("/login")
                        .userParameter("email")
                        .passwordParam("senha")
                        .user(CredenciaisDeTeste.ADMIN_EMAIL)
                        .password(CredenciaisDeTeste.ADMIN_SENHA))
                .andExpect(authenticated().withRoles("MEMBRO", "ADMIN"));
    }

    @Test
    void naoCriaOutroQuandoJaExisteAdmin() {
        usuarios.save(Usuario.admin("Outro Admin", "outro.admin@teste.local", "{noop}qualquer"));

        assertThat(adminInicial.criarSeNecessario()).isFalse();
        assertThat(usuarios.count()).isEqualTo(1);
    }
}
