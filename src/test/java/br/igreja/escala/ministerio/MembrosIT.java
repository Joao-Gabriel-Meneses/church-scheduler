package br.igreja.escala.ministerio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.Auditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.identidade.service.UsuarioResumo;
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
import br.igreja.escala.ministerio.service.MembroService;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

    @Autowired
    MembroService membroService;

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
    void adminNaoRedefineAPropriaSenhaPorPostDireto() throws Exception {
        var midia = ministerio("Mídia Própria");
        membresias.save(new Membresia(admin.getId(), midia));

        mvc.perform(post("/ministerios/{m}/membros/{u}/senha", midia.getId(), admin.getId())
                        .with(user(admin))
                        .with(csrf())
                        .param("senha", "provisoria-123"))
                .andExpect(redirectedUrl("/ministerios/" + midia.getId() + "/membros/" + admin.getId()))
                .andExpect(flash().attribute(
                                "recusa",
                                "Sua senha não mudou: para trocar a sua própria senha, use Trocar senha no início."));

        entityManager.flush();
        entityManager.clear();
        var conta = usuarios.findById(admin.getId()).orElseThrow();
        assertThat(conta.getSenhaHash()).isEqualTo("{noop}x");
        assertThat(conta.isSenhaProvisoria()).isFalse();
        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .isEmpty();
    }

    @Test
    void gerenteEditaOsDadosDoMembroQueEntraComONovoEmail() throws Exception {
        var midia = ministerio("Mídia Edição");
        var gerente = gerenteDe(midia, "gerente.edicao@teste.local");
        var ana = membroDe(midia, "ana.edicao@teste.local");

        mvc.perform(post("/ministerios/{m}/membros/{u}/editar", midia.getId(), ana.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nome", "Ana Edição")
                        .param("email", "Ana.Nova@Teste.local")
                        .param("telefone", ""))
                .andExpect(flash().attribute("sucesso", "Dados de Ana Edição salvos"));

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo("ana.nova@teste.local");
        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getAcao()).isEqualTo(AcaoAuditada.EDITAR_CONTA);
                    assertThat(registro.getDescricao())
                            .isEqualTo("Ana Edição: nome e e-mail alterados (Mídia Edição).");
                });
        mvc.perform(login("ana.nova@teste.local", "senha-original")).andExpect(authenticated());
        mvc.perform(login("ana.edicao@teste.local", "senha-original")).andExpect(unauthenticated());
    }

    @Test
    void edicaoRecusadaNaoMudaAConta() throws Exception {
        var midia = ministerio("Mídia Recusas");
        var louvor = ministerio("Louvor Recusas");
        var gerente = gerenteDe(midia, "gerente.recusas@teste.local");
        var ana = membroDe(midia, "ana.recusas@teste.local");
        var outroGerente = gerenteDe(midia, "outro.recusas@teste.local");
        var doLouvor = membroDe(louvor, "bruno.recusas@teste.local");
        var gerenteDoLouvor = gerenteDe(louvor, "lia.recusas@teste.local");

        mvc.perform(editar(gerente, midia, ana.getId(), "gerente.recusas@teste.local"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(Matchers.containsString(
                                "O e-mail gerente.recusas@teste.local já é o login de outra conta.")));
        mvc.perform(editar(gerente, midia, outroGerente.getId(), "invadido@teste.local"))
                .andExpect(flash().attribute(
                                "recusa",
                                "Os dados de outro.recusas não mudaram: a conta de um gerente ou administrador só o"
                                        + " administrador edita."));
        mvc.perform(editar(gerente, midia, doLouvor.getId(), "invadido@teste.local"))
                .andExpect(status().isNotFound());
        mvc.perform(editar(gerenteDoLouvor, midia, ana.getId(), "invadido@teste.local"))
                .andExpect(status().isForbidden());
        mvc.perform(editar(gerente, louvor, doLouvor.getId(), "invadido@teste.local"))
                .andExpect(status().isForbidden());

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findByEmail("invadido@teste.local")).isEmpty();
        assertThat(usuarios.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo("ana.recusas@teste.local");
        assertThat(auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId()))
                .isEmpty();
    }

    @Test
    void adminEditaOsDadosDeUmGerente() throws Exception {
        var midia = ministerio("Mídia Admin Edita");
        var gerente = gerenteDe(midia, "gerente.admin.edita@teste.local");

        mvc.perform(editar(admin, midia, gerente.getId(), "gerente.novo@teste.local"))
                .andExpect(flash().attributeExists("sucesso"));

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findById(gerente.getId()).orElseThrow().getEmail()).isEqualTo("gerente.novo@teste.local");
    }

    @Test
    void soOAdminDesativaAContaDosOutrosERegistraSemMinisterio() throws Exception {
        var midia = ministerio("Mídia Desativação");
        var louvor = ministerio("Louvor Desativação");
        var gerente = gerenteDe(midia, "gerente.desativacao@teste.local");
        var ana = membroDe(midia, "ana.desativacao@teste.local");
        var doLouvor = membroDe(louvor, "bruno.desativacao@teste.local");
        membresias.save(new Membresia(admin.getId(), midia));

        mvc.perform(post("/ministerios/{m}/membros/{u}/desativar", midia.getId(), ana.getId())
                        .with(user(gerente))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/{m}/membros/{u}/desativar", midia.getId(), doLouvor.getId())
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/membros/{u}/desativar", midia.getId(), admin.getId())
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(flash().attribute(
                                "recusa", "Sua conta continua ativa: o administrador não desativa a própria conta."));
        mvc.perform(post("/ministerios/{m}/membros/{u}/desativar", midia.getId(), ana.getId())
                        .with(user(admin))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "Conta de ana.desativacao desativada"));

        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findById(ana.getId()).orElseThrow().isAtivo()).isFalse();
        assertThat(usuarios.findById(doLouvor.getId()).orElseThrow().isAtivo()).isTrue();
        assertThat(usuarios.findById(admin.getId()).orElseThrow().isAtivo()).isTrue();
        assertThat(auditorias.findAll())
                .filteredOn(registro -> ana.getId().equals(registro.getAlvoUsuarioId()))
                .singleElement()
                .satisfies(registro -> {
                    assertThat(registro.getAcao()).isEqualTo(AcaoAuditada.DESATIVAR_CONTA);
                    assertThat(registro.getMinisterioId()).isNull();
                    assertThat(registro.getDescricao()).isEqualTo("Conta de ana.desativacao desativada.");
                });
        mvc.perform(get("/ministerios/{m}/membros", midia.getId()).with(user(gerente)))
                .andExpect(content().string(Matchers.containsString("ana.desativacao · Desativada")));
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
    void servemNoMinisterioSoOsMembrosAtivosComHabilitacao() {
        var midia = ministerio("Mídia Servem");
        var louvor = ministerio("Louvor Servem");
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        var transmissao = funcoes.save(new Funcao(midia, "Transmissão", Icone.VIDEO, 1, 1));
        var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
        var ana = membroDe(midia, "ana.servem@teste.local");
        var bia = membroDe(midia, "bia.servem@teste.local");
        var caio = membroDe(midia, "caio.servem@teste.local");
        habilitacoes.save(new Habilitacao(ana.getId(), projecao, iniciante));
        habilitacoes.save(new Habilitacao(ana.getId(), transmissao, iniciante));
        habilitacoes.save(new Habilitacao(bia.getId(), projecao, iniciante));
        bia.desativar();
        membresias.save(new Membresia(ana.getId(), louvor));
        habilitar(ana, louvor);
        entityManager.flush();

        assertThat(membroService.queServem(midia.getId()))
                .as("Bia está desativada e Caio não tem habilitação")
                .extracting(UsuarioResumo::id)
                .containsExactly(ana.getId());
        assertThat(membroService.ministeriosEmQueServe(ana.getId()))
                .extracting(Ministerio::getNome)
                .containsExactly("Louvor Servem", "Mídia Servem");
        assertThat(membroService.buscarQueServe(midia.getId(), ana.getId()).id())
                .isEqualTo(ana.getId());
        assertThatThrownBy(() -> membroService.buscarQueServe(midia.getId(), caio.getId()))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> membroService.buscarQueServe(midia.getId(), bia.getId()))
                .isInstanceOf(NaoEncontradoException.class);
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

    @Test
    void habilitacoesSoAceitamFuncaoENivelDoProprioMinisterio() throws Exception {
        var midia = ministerio("Mídia Habilitações");
        var louvor = ministerio("Louvor Habilitações");
        var gerente = gerenteDe(midia, "gerente.habilitacoes@teste.local");
        var ana = membroDe(midia, "ana.habilitacoes@teste.local");
        var doLouvor = membroDe(louvor, "bia.habilitacoes@teste.local");
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        var experiente = niveis.save(new Nivel(midia, "Experiente", 2));
        var solista = niveis.save(new Nivel(louvor, "Solista", 1));
        var vocal = funcoes.save(new Funcao(louvor, "Vocal", Icone.MIC, 1, 3));
        long m = midia.getId();

        mvc.perform(post("/ministerios/{m}/membros/{u}/habilitacoes", m, ana.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nivel-" + projecao.getId(), solista.getId().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/membros/{u}/habilitacoes", m, ana.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nivel-" + vocal.getId(), experiente.getId().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/membros/{u}/habilitacoes", m, doLouvor.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nivel-" + projecao.getId(), experiente.getId().toString()))
                .andExpect(status().isNotFound());
        assertThat(habilitacoes.findByFuncaoMinisterioId(m)).isEmpty();

        mvc.perform(post("/ministerios/{m}/membros/{u}/habilitacoes", m, ana.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nivel-" + projecao.getId(), experiente.getId().toString()))
                .andExpect(flash().attribute("sucesso", "Habilitações de ana.habilitacoes salvas"));
        assertThat(habilitacoes.findByUsuarioIdAndFuncaoId(ana.getId(), projecao.getId()))
                .get()
                .extracting(habilitacao -> habilitacao.getNivel().getNome())
                .isEqualTo("Experiente");
    }

    private static MockHttpServletRequestBuilder editar(
            UsuarioAutenticado quem, Ministerio ministerio, Long usuarioId, String email) {
        return post("/ministerios/{m}/membros/{u}/editar", ministerio.getId(), usuarioId)
                .with(user(quem))
                .with(csrf())
                .param("nome", "Nome Novo")
                .param("email", email);
    }

    private static RequestBuilder login(String email, String senha) {
        return formLogin("/login")
                .userParameter("email")
                .passwordParam("senha")
                .user(email)
                .password(senha);
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
