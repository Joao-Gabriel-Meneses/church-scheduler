package br.igreja.escala.ministerio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.Auditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Membros ponta a ponta no Oracle: admin, gerente e membro, com tentativas cruzadas entre ministérios. */
@TesteDeIntegracao
@Transactional
class MembrosIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    FuncaoRepository funcoes;

    @Autowired
    NivelRepository niveis;

    @Autowired
    HabilitacaoRepository habilitacoes;

    @Autowired
    AuditoriaRepository auditorias;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EntityManager entityManager;

    private UsuarioAutenticado admin;

    @BeforeEach
    void criaOAdmin() {
        admin = new UsuarioAutenticado(
                usuarios.save(Usuario.admin("Admin Membros", "admin.membros@teste.local", "{noop}x")));
    }

    @Test
    void adminCriaOMinisterioENomeiaOGerenteQueCadastraUmMembroQueTrocaASenha() throws Exception {
        mvc.perform(post("/admin/ministerios")
                        .with(user(admin))
                        .with(csrf())
                        .param("nome", "Mídia Membros")
                        .param("cor", "MINT")
                        .param("icone", "MONITOR"))
                .andExpect(redirectedUrl("/admin/ministerios"));
        var midia = ministerios.findAllByOrderByNomeAsc().stream()
                .filter(ministerio -> ministerio.getNome().equals("Mídia Membros"))
                .findFirst()
                .orElseThrow();

        long joao = cadastrar(admin, midia, "João Gerente", "joao.membros@teste.local", "provisoria-joao");
        mvc.perform(post("/ministerios/{m}/membros/{u}/gerente", midia.getId(), joao)
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "João Gerente agora é gerente"));
        // Com a senha provisória, o João só abriria a troca de senha (SenhaProvisoriaInterceptor).
        var contaDoJoao = usuarios.findById(joao).orElseThrow();
        contaDoJoao.definirSenha(passwordEncoder.encode("senha-do-joao"));
        var gerente = new UsuarioAutenticado(contaDoJoao);

        cadastrar(gerente, midia, "Carla Dias", "carla.membros@teste.local", "provisoria-carla");
        var sessao = mvc.perform(formLogin("/login")
                        .userParameter("email")
                        .passwordParam("senha")
                        .user("carla.membros@teste.local")
                        .password("provisoria-carla"))
                .andExpect(authenticated())
                .andReturn()
                .getRequest()
                .getSession(false);
        mvc.perform(get("/").session((MockHttpSession) sessao)).andExpect(redirectedUrl("/conta/senha"));

        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .extracting(Auditoria::getAcao)
                .containsExactly(AcaoAuditada.NOMEAR_GERENTE);
        assertThat(membresias.ministeriosGerenciadosPor(joao))
                .extracting(Ministerio::getId)
                .containsExactly(midia.getId());
    }

    @Test
    void emailQueJaTemContaSoEntraNoMinisterioESenhaNaoMuda() throws Exception {
        var midia = ministerio("Mídia Conta");
        var ana = usuarios.save(
                Usuario.membro("Ana Conta", "ana.conta@teste.local", passwordEncoder.encode("senha-da-ana")));

        mvc.perform(post("/ministerios/{m}/membros", midia.getId())
                        .with(user(admin))
                        .with(csrf())
                        .param("nome", "Outro Nome")
                        .param("email", "ANA.conta@teste.local")
                        .param("senhaProvisoria", "outra-senha"))
                .andExpect(flash().attribute("sucesso", "Ana Conta já tinha conta e entrou no ministério"));

        entityManager.flush();
        entityManager.clear();
        var lida = usuarios.findById(ana.getId()).orElseThrow();
        assertThat(lida.getNome()).isEqualTo("Ana Conta");
        assertThat(passwordEncoder.matches("senha-da-ana", lida.getSenhaHash())).isTrue();
        assertThat(membresias.existsByUsuarioIdAndMinisterioId(ana.getId(), midia.getId()))
                .isTrue();
    }

    @Test
    void gerenteNaoRedefineSenhaDeQuemNaoEDoMinisterioNemDeOutroGerente() throws Exception {
        var midia = ministerio("Mídia Senhas");
        var louvor = ministerio("Louvor Senhas");
        var gerenteDaMidia = gerenteDe(midia, "gerente.senhas@teste.local");
        var doLouvor = membroDe(louvor, "bruno.senhas@teste.local");
        var gerenteDoLouvor = membroDe(louvor, "lia.senhas@teste.local");
        membresias
                .findByUsuarioIdAndMinisterioId(gerenteDoLouvor.getId(), louvor.getId())
                .orElseThrow()
                .tornarGerente();
        membresias.save(new Membresia(gerenteDoLouvor.getId(), midia));

        mvc.perform(post("/ministerios/{m}/membros/{u}/senha", midia.getId(), doLouvor.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("senha", "invadida-123"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/membros/{u}/senha", louvor.getId(), doLouvor.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("senha", "invadida-123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/{m}/membros/{u}/senha", midia.getId(), gerenteDoLouvor.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("senha", "invadida-123"))
                .andExpect(flash().attributeExists("recusa"));

        entityManager.flush();
        entityManager.clear();
        assertThat(passwordEncoder.matches(
                        "invadida-123",
                        usuarios.findById(doLouvor.getId()).orElseThrow().getSenhaHash()))
                .isFalse();
        assertThat(passwordEncoder.matches(
                        "invadida-123",
                        usuarios.findById(gerenteDoLouvor.getId()).orElseThrow().getSenhaHash()))
                .isFalse();
        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .isEmpty();
    }

    @Test
    void removerTiraAsHabilitacoesDesteMinisterioEMantemAsDeOutro() throws Exception {
        var midia = ministerio("Mídia Remoção");
        var louvor = ministerio("Louvor Remoção");
        var gerente = gerenteDe(midia, "gerente.remocao@teste.local");
        var ana = membroDe(midia, "ana.remocao@teste.local");
        membresias.save(new Membresia(ana.getId(), louvor));
        habilitar(ana, midia);
        habilitar(ana, louvor);

        mvc.perform(post("/ministerios/{m}/membros/{u}/remover", midia.getId(), ana.getId())
                        .with(user(gerente))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/" + midia.getId() + "/membros"));

        assertThat(membresias.existsByUsuarioIdAndMinisterioId(ana.getId(), midia.getId()))
                .isFalse();
        assertThat(membresias.existsByUsuarioIdAndMinisterioId(ana.getId(), louvor.getId()))
                .isTrue();
        assertThat(habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(ana.getId(), midia.getId()))
                .isEmpty();
        assertThat(habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(ana.getId(), louvor.getId()))
                .hasSize(1);
        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .extracting(Auditoria::getAcao)
                .containsExactly(AcaoAuditada.REMOVER_MEMBRO);
    }

    @Test
    void paginaDosMembrosRenderizaComOsDadosDoBanco() throws Exception {
        var midia = ministerio("Mídia Página");
        var gerente = gerenteDe(midia, "gerente.pagina@teste.local");
        var ana = membroDe(midia, "ana.pagina@teste.local");
        habilitar(ana, midia);

        mvc.perform(get("/ministerios/{m}/membros", midia.getId()).with(user(gerente)))
                .andExpect(status().isOk());
        mvc.perform(get("/ministerios/{m}/membros/{u}", midia.getId(), ana.getId())
                        .with(user(gerente)))
                .andExpect(status().isOk());
    }

    private long cadastrar(UsuarioAutenticado quem, Ministerio ministerio, String nome, String email, String senha)
            throws Exception {
        mvc.perform(post("/ministerios/{m}/membros", ministerio.getId())
                        .with(user(quem))
                        .with(csrf())
                        .param("nome", nome)
                        .param("email", email)
                        .param("senhaProvisoria", senha))
                .andExpect(status().is3xxRedirection());
        return usuarios.findByEmail(email).orElseThrow().getId();
    }

    private Ministerio ministerio(String nome) {
        return ministerios.save(new Ministerio(nome, CorDoMinisterio.MINT, Icone.MONITOR));
    }

    private UsuarioAutenticado gerenteDe(Ministerio ministerio, String email) {
        var gerente = membroDe(ministerio, email);
        membresias
                .findByUsuarioIdAndMinisterioId(gerente.getId(), ministerio.getId())
                .orElseThrow()
                .tornarGerente();
        return new UsuarioAutenticado(gerente);
    }

    private Usuario membroDe(Ministerio ministerio, String email) {
        var usuario = usuarios.save(Usuario.membro(
                email.substring(0, email.indexOf('@')), email, passwordEncoder.encode("senha-original")));
        membresias.save(new Membresia(usuario.getId(), ministerio));
        return usuario;
    }

    private void habilitar(Usuario usuario, Ministerio ministerio) {
        var funcao = funcoes.save(new Funcao(ministerio, "Função " + usuario.getId(), Icone.MONITOR, 1, 1));
        var nivel = niveis.save(new Nivel(ministerio, "Nível " + usuario.getId(), 1));
        habilitacoes.save(new Habilitacao(usuario.getId(), funcao, nivel));
    }
}
