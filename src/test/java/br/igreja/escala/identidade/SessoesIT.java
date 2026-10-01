package br.igreja.escala.identidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessões abertas antes de a senha mudar, ponta a ponta. Sem {@code @Transactional}: as sessões só caem depois do
 * commit (SessoesAbertas), e numa transação de teste o commit nunca acontece. Os dados são apagados no
 * {@code @AfterEach}.
 */
@TesteDeIntegracao
class SessoesIT {

    private static final String ANA = "ana.sessoes@teste.local";
    private static final String PAULA = "paula.sessoes@teste.local";
    private static final String SENHA_DA_ANA = "senha-da-ana";
    private static final String SENHA_DA_PAULA = "senha-da-paula";

    @Autowired
    MockMvc mvc;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long ministerioId;
    private Long anaId;
    private UsuarioAutenticado gerente;
    private UsuarioAutenticado admin;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Sessões", CorDoMinisterio.MINT, Icone.MONITOR));
            var paula = usuarios.save(Usuario.membro("Paula Sessões", PAULA, passwordEncoder.encode(SENHA_DA_PAULA)));
            var ana = usuarios.save(Usuario.membro("Ana Sessões", ANA, passwordEncoder.encode(SENHA_DA_ANA)));
            var membresiaDaPaula = new Membresia(paula.getId(), midia);
            membresiaDaPaula.tornarGerente();
            membresias.save(membresiaDaPaula);
            membresias.save(new Membresia(ana.getId(), midia));
            ministerioId = midia.getId();
            anaId = ana.getId();
            gerente = new UsuarioAutenticado(paula);
            admin = new UsuarioAutenticado(
                    usuarios.save(Usuario.admin("Admin Sessões", "admin.sessoes@teste.local", "{noop}x")));
        });
    }

    @AfterEach
    void apagaOsDados() {
        transacao.executeWithoutResult(status -> {
            // Desativar e reativar ficam na auditoria sem ministério.
            jdbc.update(
                    "delete from auditoria where ministerio_id = ? or autor_id in"
                            + " (select id from usuario where email like '%.sessoes@teste.local')",
                    ministerioId);
            jdbc.update("delete from membresia where ministerio_id = ?", ministerioId);
            jdbc.update("delete from ministerio where id = ?", ministerioId);
            jdbc.update("delete from usuario where email like '%.sessoes@teste.local'");
        });
    }

    @Test
    void redefinirASenhaDerrubaTodasAsSessoesAbertasDoMembro() throws Exception {
        var celular = entrar(ANA, SENHA_DA_ANA);
        var computador = entrar(ANA, SENHA_DA_ANA);
        mvc.perform(get("/").session(celular)).andExpect(status().isOk());

        redefinirASenhaDaAna("provisoria-nova");

        mvc.perform(get("/").session(celular)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(get("/").session(computador)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(login(ANA, SENHA_DA_ANA)).andExpect(unauthenticated());
        mvc.perform(login(ANA, "provisoria-nova")).andExpect(authenticated());
    }

    @Test
    void oContinuarConectadoTambemDeixaDeValer() throws Exception {
        var entrada = mvc.perform(login(ANA, SENHA_DA_ANA).param("lembrar", "on"))
                .andExpect(authenticated())
                .andReturn();
        var sessao = (MockHttpSession) entrada.getRequest().getSession(false);
        Cookie lembrar = entrada.getResponse().getCookie("remember-me");
        assertThat(lembrar).isNotNull();
        mvc.perform(get("/").cookie(lembrar)).andExpect(status().isOk());

        redefinirASenhaDaAna("provisoria-nova");

        mvc.perform(get("/").session(sessao).cookie(lembrar))
                .andExpect(redirectedUrl("/login?expirou"))
                .andExpect(cookie().maxAge("remember-me", 0));
        // Um cookie guardado em outro aparelho, sem sessão: foi assinado com o hash da senha antiga.
        mvc.perform(get("/").cookie(lembrar)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void trocarAPropriaSenhaDerrubaAsOutrasSessoesEMantemAAtual() throws Exception {
        var celular = entrar(ANA, SENHA_DA_ANA);
        var computador = entrar(ANA, SENHA_DA_ANA);

        mvc.perform(post("/conta/senha")
                        .session(computador)
                        .with(csrf())
                        .param("senhaAtual", SENHA_DA_ANA)
                        .param("novaSenha", "senha-nova-da-ana")
                        .param("confirmacao", "senha-nova-da-ana"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(computador)).andExpect(status().isOk());
        mvc.perform(get("/").session(celular)).andExpect(redirectedUrl("/login?expirou"));
    }

    @Test
    void asSessoesDasOutrasPessoasContinuam() throws Exception {
        var daAna = entrar(ANA, SENHA_DA_ANA);
        var daPaula = entrar(PAULA, SENHA_DA_PAULA);

        redefinirASenhaDaAna("provisoria-nova");

        mvc.perform(get("/").session(daAna)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(get("/").session(daPaula)).andExpect(status().isOk());
    }

    @Test
    void desativarAContaDerrubaAsSessoesENaoDeixaEntrarAteReativar() throws Exception {
        var entrada = mvc.perform(login(ANA, SENHA_DA_ANA).param("lembrar", "on"))
                .andExpect(authenticated())
                .andReturn();
        var sessao = (MockHttpSession) entrada.getRequest().getSession(false);
        Cookie lembrar = entrada.getResponse().getCookie("remember-me");

        mvc.perform(post("/ministerios/{m}/membros/{u}/desativar", ministerioId, anaId)
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "Conta de Ana Sessões desativada"));

        mvc.perform(get("/").session(sessao)).andExpect(redirectedUrl("/login?expirou"));
        mvc.perform(get("/").cookie(lembrar)).andExpect(redirectedUrl("/login"));
        mvc.perform(login(ANA, SENHA_DA_ANA)).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?erro"));

        mvc.perform(post("/ministerios/{m}/membros/{u}/reativar", ministerioId, anaId)
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "Conta de Ana Sessões reativada"));
        mvc.perform(login(ANA, SENHA_DA_ANA)).andExpect(authenticated());
    }

    private void redefinirASenhaDaAna(String senha) throws Exception {
        mvc.perform(post("/ministerios/{m}/membros/{u}/senha", ministerioId, anaId)
                        .with(user(gerente))
                        .with(csrf())
                        .param("senha", senha))
                .andExpect(flash().attribute("sucesso", "Senha provisória de Ana Sessões redefinida"));
    }

    private MockHttpSession entrar(String email, String senha) throws Exception {
        var resultado =
                mvc.perform(login(email, senha)).andExpect(authenticated()).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private static MockHttpServletRequestBuilder login(String email, String senha) {
        return post("/login").with(csrf()).param("email", email).param("senha", senha);
    }
}
