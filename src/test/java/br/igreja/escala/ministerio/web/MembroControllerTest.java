package br.igreja.escala.ministerio.web;

import static br.igreja.escala.AcessoDeTeste.ADMIN;
import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.TesteDeRotaDoGerente;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.identidade.service.DadosDaConta;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.DadosDoMembro;
import br.igreja.escala.ministerio.service.HabilitacaoService;
import br.igreja.escala.ministerio.service.LinhaDeHabilitacao;
import br.igreja.escala.ministerio.service.MembroResumo;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import br.igreja.escala.ministerio.service.ResultadoDoCadastro;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(MembroController.class)
class MembroControllerTest {

    private static final UsuarioResumo ANA =
            new UsuarioResumo(30L, "Ana Souza", "ana@x.com", "(11) 98888-7777", false, true, true);
    private static final MembroResumo ANA_NA_MIDIA = new MembroResumo(ANA, false, List.of("Projeção · Experiente"));

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    MembroService membros;

    @MockitoBean
    HabilitacaoService habilitacoes;

    @MockitoBean
    NivelService niveis;

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
        when(membros.buscar(1L, 30L)).thenReturn(ANA_NA_MIDIA);
        when(membros.podeMexerNaConta(eq(ANA_NA_MIDIA), any())).thenReturn(true);
    }

    @Test
    void membroComumNaoVeNemMexeNosMembros() throws Exception {
        mvc.perform(get("/ministerios/1/membros").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/1/membros/30").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(cadastrar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/senha")
                        .with(user(MEMBRO))
                        .with(csrf())
                        .param("senha", "nova-senha"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/remover").with(user(MEMBRO)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/1/membros/30/editar").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(editar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/desativar")
                        .with(user(MEMBRO))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/reativar")
                        .with(user(MEMBRO))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(membros, never()).desativarConta(anyLong(), anyLong(), any());
        verify(membros, never()).reativarConta(anyLong(), anyLong(), any());
        verify(membros, never()).editarConta(anyLong(), anyLong(), any(), any());
        verify(membros, never()).cadastrar(anyLong(), any());
        verify(membros, never()).redefinirSenha(anyLong(), anyLong(), anyString(), any());
        verify(membros, never()).remover(anyLong(), anyLong(), any());
    }

    @Test
    void gerenteDaMidiaNaoMexeNosMembrosDoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/membros").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(cadastrar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/membros/30/senha")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("senha", "nova-senha"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/membros/30/remover")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/2/membros/30/editar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(editar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/membros/30/desativar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(membros, never()).desativarConta(anyLong(), anyLong(), any());
        verify(membros, never()).editarConta(anyLong(), anyLong(), any(), any());
        verify(membros, never()).cadastrar(anyLong(), any());
        verify(membros, never()).redefinirSenha(anyLong(), anyLong(), anyString(), any());
        verify(membros, never()).remover(anyLong(), anyLong(), any());
    }

    @Test
    void soOAdminNomeiaERemoveGerentesMesmoNoMinisterioDoGerente() throws Exception {
        mvc.perform(post("/ministerios/1/membros/30/gerente")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/gerente/remover")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(membros, never()).tornarGerente(anyLong(), anyLong(), any());

        when(membros.tornarGerente(1L, 30L, ADMIN)).thenReturn(ANA);
        mvc.perform(post("/ministerios/1/membros/30/gerente").with(user(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Ana Souza agora é gerente"));
    }

    @Test
    void soOAdminDesativaEReativaContasMesmoNoMinisterioDoGerente() throws Exception {
        mvc.perform(post("/ministerios/1/membros/30/desativar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/reativar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(membros, never()).desativarConta(anyLong(), anyLong(), any());
        verify(membros, never()).reativarConta(anyLong(), anyLong(), any());

        when(membros.desativarConta(1L, 30L, ADMIN)).thenReturn(ANA);
        when(membros.reativarConta(1L, 30L, ADMIN)).thenReturn(ANA);
        mvc.perform(post("/ministerios/1/membros/30/desativar")
                        .with(user(ADMIN))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Conta de Ana Souza desativada"));
        mvc.perform(post("/ministerios/1/membros/30/reativar").with(user(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Conta de Ana Souza reativada"));
    }

    @Test
    void desativacaoRecusadaVoltaComOMotivoEOutroMinisterioE404() throws Exception {
        when(membros.desativarConta(1L, 1L, ADMIN))
                .thenThrow(RegraVioladaException.geral(
                        "Sua conta continua ativa: o administrador não desativa a própria conta."));
        when(membros.desativarConta(1L, 99L, ADMIN)).thenThrow(new NaoEncontradoException("Usuário 99"));

        mvc.perform(post("/ministerios/1/membros/1/desativar").with(user(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros/1"))
                .andExpect(flash().attribute(
                                "recusa", "Sua conta continua ativa: o administrador não desativa a própria conta."));
        mvc.perform(post("/ministerios/1/membros/99/desativar")
                        .with(user(ADMIN))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminVeDesativarComConfirmacaoEOGerenteNao() throws Exception {
        assertThat(pagina(get("/ministerios/1/membros/30").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .contains("popovertarget=\"desativar-conta\"", "id=\"desativar-conta\"")
                .contains("Desativar a conta de Ana Souza?", "action=\"/ministerios/1/membros/30/desativar\"")
                .doesNotContain("Reativar conta", "Conta desativada");
        assertThat(pagina(
                        get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .doesNotContain("desativar-conta", "/desativar");
    }

    @Test
    void contaDesativadaApareceComBadgeEOAdminReativa() throws Exception {
        var desativada = new MembroResumo(
                new UsuarioResumo(30L, "Ana Souza", "ana@x.com", null, false, false, false), false, List.of());
        when(membros.buscar(1L, 30L)).thenReturn(desativada);
        when(membros.podeMexerNaConta(eq(desativada), any())).thenReturn(true);

        assertThat(pagina(get("/ministerios/1/membros/30").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .contains("rt-badge rt-badge--locked", "Conta desativada", "Só o administrador reativa.")
                .contains("action=\"/ministerios/1/membros/30/reativar\"", "Reativar conta")
                .doesNotContain("id=\"desativar-conta\"");
        assertThat(pagina(
                        get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("Conta desativada")
                .doesNotContain("Reativar conta");
    }

    @Test
    void naPropriaPaginaOAdminNaoSeDesativa() throws Exception {
        var proprio = new MembroResumo(
                new UsuarioResumo(1L, "Admin", "admin@x.com", null, true, false, true), false, List.of());
        when(membros.buscar(1L, 1L)).thenReturn(proprio);
        when(membros.podeMexerNaConta(eq(proprio), any())).thenReturn(true);

        assertThat(pagina(get("/ministerios/1/membros/1").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .doesNotContain("desativar-conta");
    }

    @Test
    void listaMostraAContaDesativadaNoCelularENoDesktop() throws Exception {
        var desativada = new MembroResumo(
                new UsuarioResumo(31L, "Bruno Lima", "bruno@x.com", null, false, false, false), true, List.of());
        when(membros.listar(1L)).thenReturn(List.of(ANA_NA_MIDIA, desativada));

        String html = pagina(get("/ministerios/1/membros").with(user(GERENTE_DA_MIDIA)), MembroController.LISTA);

        assertThat(html)
                .contains("Bruno Lima · Gerente · Desativada")
                .containsOnlyOnce("rt-badge rt-badge--locked")
                .doesNotContain("Ana Souza · Desativada");
    }

    @Test
    void listaNoCelularENoDesktopComHabilitacoesEAcesso() throws Exception {
        var gerente = new MembroResumo(
                new UsuarioResumo(10L, "Gerente da Mídia", "g@x.com", null, false, false, true), true, List.of());
        when(membros.listar(1L)).thenReturn(List.of(ANA_NA_MIDIA, gerente));

        String html = pagina(get("/ministerios/1/membros").with(user(GERENTE_DA_MIDIA)), MembroController.LISTA);

        assertThat(html)
                .contains("Mídia — Membros", "Cadastrar membro")
                .contains("Projeção · Experiente", "href=\"/ministerios/1/membros/30\"")
                .contains("Gerente da Mídia · Gerente", "Sem habilitação ainda")
                .contains("<span class=\"rt-avatar\" aria-hidden=\"true\">AS</span>")
                .contains("Senha provisória", "Senha própria", "(11) 98888-7777");
    }

    @Test
    void listaVaziaExplicaComoComecar() throws Exception {
        when(membros.listar(1L)).thenReturn(List.of());

        assertThat(pagina(get("/ministerios/1/membros").with(user(GERENTE_DA_MIDIA)), MembroController.LISTA))
                .contains("Ninguém no ministério ainda.");
    }

    @Test
    void cadastroNovoVaiParaAPaginaDoMembro() throws Exception {
        when(membros.cadastrar(1L, new DadosDoMembro("Ana Souza", "ana@x.com", "", "provisoria1")))
                .thenReturn(new ResultadoDoCadastro(ANA, false));

        mvc.perform(cadastrar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Ana Souza entrou no ministério com a senha provisória"));
    }

    @Test
    void cadastroDeQuemJaTemContaAvisaQueASenhaNaoMudou() throws Exception {
        when(membros.cadastrar(anyLong(), any())).thenReturn(new ResultadoDoCadastro(ANA, true));

        mvc.perform(cadastrar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Ana Souza já tinha conta e entrou no ministério"));
    }

    @Test
    void cadastroDeContaDesativadaAvisaQueSoOAdminReativa() throws Exception {
        var desativada = new UsuarioResumo(30L, "Ana Souza", "ana@x.com", null, false, false, false);
        when(membros.cadastrar(anyLong(), any())).thenReturn(new ResultadoDoCadastro(desativada, true));

        mvc.perform(cadastrar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute(
                                "sucesso",
                                "Ana Souza entrou no ministério, mas a conta está desativada: só o administrador"
                                        + " reativa"));
    }

    @Test
    void formularioInvalidoVoltaComOsErrosNosCampos() throws Exception {
        String html = pagina(
                post("/ministerios/1/membros")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "")
                        .param("email", "ana@")
                        .param("telefone", "abc")
                        .param("senhaProvisoria", "curta"),
                "ministerio/membro-form");

        assertThat(html)
                .contains("Informe o nome.", "Informe um e-mail válido, como ana@exemplo.com.")
                .contains("Use só números, espaços, parênteses, + e -", "A senha precisa ter de 8 a 64 caracteres.");
        verify(membros, never()).cadastrar(anyLong(), any());
    }

    @Test
    void senhaProvisoriaEmBrancoTemUmaMensagemSo() throws Exception {
        String html = pagina(
                post("/ministerios/1/membros")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Ana")
                        .param("email", "ana@x.com")
                        .param("senhaProvisoria", ""),
                "ministerio/membro-form");

        assertThat(html)
                .containsOnlyOnce("A senha precisa ter de 8 a 64 caracteres.")
                .doesNotContain("Defina a senha provisória.");
    }

    @Test
    void quemJaEstaNoMinisterioApareceNoCampoEmail() throws Exception {
        when(membros.cadastrar(anyLong(), any()))
                .thenThrow(new RegraVioladaException("email", "Ana Souza já está neste ministério."));

        assertThat(pagina(cadastrar(1).with(user(GERENTE_DA_MIDIA)), "ministerio/membro-form"))
                .contains("id=\"email-erro\"", "Ana Souza já está neste ministério.");
    }

    @Test
    void paginaDoMembroComAsAcoesDoGerente() throws Exception {
        String html = pagina(
                get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)), MembroController.PAGINA_DO_MEMBRO);

        assertThat(html)
                .contains("<h1 class=\"text-title\">Ana Souza</h1>", "Ainda com a senha provisória")
                .contains("ana@x.com", "(11) 98888-7777")
                .contains("popovertarget=\"redefinir-senha\"", "action=\"/ministerios/1/membros/30/senha\"")
                .contains("minlength=\"8\"", "maxlength=\"64\"")
                .contains("popovertarget=\"remover-membro\"", "action=\"/ministerios/1/membros/30/remover\"")
                .contains("href=\"/ministerios/1/membros/30/editar\"", "Editar dados")
                .doesNotContain("Tornar gerente");
    }

    @Test
    void adminVeTornarGerente() throws Exception {
        assertThat(pagina(get("/ministerios/1/membros/30").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .contains("action=\"/ministerios/1/membros/30/gerente\"", "Tornar gerente");
    }

    @Test
    void contaDeGerenteNaoMostraRedefinirSenhaNemRemoverParaOGerente() throws Exception {
        var outroGerente = new MembroResumo(ANA, true, List.of());
        when(membros.buscar(1L, 30L)).thenReturn(outroGerente);
        when(membros.podeMexerNaConta(eq(outroGerente), any())).thenReturn(false);

        assertThat(pagina(
                        get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("Os dados e a senha de gerentes e administradores só o administrador muda.")
                .doesNotContain("id=\"redefinir-senha\"", "id=\"remover-membro\"", "/membros/30/editar");
    }

    @Test
    void naPropriaPaginaOGerenteNaoSeRemoveNemRedefineASenha() throws Exception {
        var proprio = new MembroResumo(
                new UsuarioResumo(10L, "Gerente da Mídia", "g@x.com", null, false, false, true), true, List.of());
        when(membros.buscar(1L, 10L)).thenReturn(proprio);
        when(membros.podeMexerNaConta(eq(proprio), any())).thenReturn(true);

        assertThat(pagina(get("/ministerios/1/membros/10").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .contains("id=\"redefinir-senha\"");
        assertThat(pagina(
                        get("/ministerios/1/membros/10").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("Esta é a sua conta.", "use Minha conta ou Trocar senha no início")
                .doesNotContain("id=\"redefinir-senha\"", "id=\"remover-membro\"", "/membros/10/editar");
    }

    @Test
    void redefineASenhaEVoltaComToastOuComOMotivo() throws Exception {
        when(membros.redefinirSenha(1L, 30L, "nova-senha", GERENTE_DA_MIDIA)).thenReturn(ANA);
        when(membros.redefinirSenha(1L, 30L, "curta", GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("A senha de Ana Souza não mudou."));

        mvc.perform(post("/ministerios/1/membros/30/senha")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("senha", "nova-senha"))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Senha provisória de Ana Souza redefinida"));
        mvc.perform(post("/ministerios/1/membros/30/senha")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("senha", "curta"))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("recusa", "A senha de Ana Souza não mudou."));
    }

    @Test
    void removeEVoltaParaALista() throws Exception {
        when(membros.remover(1L, 30L, GERENTE_DA_MIDIA)).thenReturn(ANA);

        mvc.perform(post("/ministerios/1/membros/30/remover")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros"))
                .andExpect(flash().attribute("sucesso", "Ana Souza saiu do ministério"));
    }

    @Test
    void remocaoRecusadaFicaNaPaginaDoMembroComOMotivo() throws Exception {
        when(membros.remover(1L, 30L, GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("Ana Souza continua no ministério."));

        mvc.perform(post("/ministerios/1/membros/30/remover")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("recusa", "Ana Souza continua no ministério."));
        assertThat(pagina(
                        get("/ministerios/1/membros/30")
                                .with(user(GERENTE_DA_MIDIA))
                                .flashAttr("recusa", "Ana Souza continua no ministério."),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("class=\"rt-alert\" role=\"alert\"", "Ana Souza continua no ministério.");
    }

    @Test
    void habilitacoesTemUmSelectPorFuncaoComONivelAtual() throws Exception {
        var midia = Exemplos.midia();
        when(habilitacoes.doMembro(1L, 30L))
                .thenReturn(List.of(
                        new LinhaDeHabilitacao(100L, "Projeção", Icone.MONITOR, 201L),
                        new LinhaDeHabilitacao(101L, "Transmissão", Icone.VIDEO, null)));
        when(niveis.listar(1L)).thenReturn(List.of(Exemplos.iniciante(midia), Exemplos.experiente(midia)));

        String html = pagina(
                get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)), MembroController.PAGINA_DO_MEMBRO);

        assertThat(html)
                .contains("action=\"/ministerios/1/membros/30/habilitacoes\"")
                .contains("<label class=\"rt-field__label\" for=\"nivel-100\">Projeção</label>")
                .contains("<option value=\"201\" selected=\"selected\">Experiente</option>")
                .contains("name=\"nivel-101\"", "<option value=\"\">Sem habilitação</option>")
                .contains("Salvar habilitações");
    }

    @Test
    void semFuncoesOuNiveisAvisaComoComecar() throws Exception {
        when(habilitacoes.doMembro(1L, 30L)).thenReturn(List.of());

        assertThat(pagina(
                        get("/ministerios/1/membros/30").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("O ministério ainda não tem funções", "href=\"/ministerios/1/funcoes\"")
                .doesNotContain("Salvar habilitações");
    }

    @Test
    void salvaAsHabilitacoesComCampoVazioTirandoAHabilitacao() throws Exception {
        mvc.perform(post("/ministerios/1/membros/30/habilitacoes")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nivel-100", "201")
                        .param("nivel-101", ""))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Habilitações de Ana Souza salvas"));

        var esperado = new HashMap<Long, Long>();
        esperado.put(100L, 201L);
        esperado.put(101L, null);
        verify(habilitacoes).definir(1L, 30L, esperado);
    }

    @Test
    void idQueNaoENumeroE400() throws Exception {
        mvc.perform(post("/ministerios/1/membros/30/habilitacoes")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nivel-abc", "201"))
                .andExpect(status().isBadRequest());
        verify(habilitacoes, never()).definir(anyLong(), anyLong(), any());
    }

    @Test
    void gerenteDeOutroMinisterioNaoMexeNasHabilitacoes() throws Exception {
        mvc.perform(post("/ministerios/2/membros/30/habilitacoes")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nivel-100", "201"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/habilitacoes")
                        .with(user(MEMBRO))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/habilitacoes").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        verify(habilitacoes, never()).definir(anyLong(), anyLong(), any());
    }

    @Test
    void pessoaDeOutroMinisterioE404() throws Exception {
        when(membros.buscar(1L, 99L)).thenThrow(new NaoEncontradoException("Usuário 99"));
        when(membros.buscarParaEditar(eq(1L), eq(99L), any())).thenThrow(new NaoEncontradoException("Usuário 99"));
        when(membros.editarConta(eq(1L), eq(99L), any(), any())).thenThrow(new NaoEncontradoException("Usuário 99"));

        mvc.perform(get("/ministerios/1/membros/99").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/ministerios/1/membros/99/editar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/1/membros/99/editar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Ana Souza")
                        .param("email", "ana@x.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    void formularioDosDadosVemPreenchidoEAvisaQueOEmailEOLogin() throws Exception {
        when(membros.buscarParaEditar(1L, 30L, GERENTE_DA_MIDIA)).thenReturn(ANA_NA_MIDIA);

        assertThat(pagina(
                        get("/ministerios/1/membros/30/editar").with(user(GERENTE_DA_MIDIA)),
                        MembroController.DADOS_DA_CONTA))
                .contains("<h1 class=\"text-title\">Editar dados</h1>", "Mídia · Membros · Ana Souza")
                .contains("action=\"/ministerios/1/membros/30/editar\"")
                .contains("value=\"Ana Souza\"", "value=\"ana@x.com\"", "value=\"(11) 98888-7777\"")
                .contains("É o login da pessoa. Se mudar, avise: ela passa a entrar com o novo e-mail.")
                .contains("href=\"/ministerios/1/membros/30\"");
    }

    @Test
    void salvaOsDadosEVoltaParaOMembroComToast() throws Exception {
        var dados = new DadosDaConta("Ana Souza", "ana.souza@x.com", "(11) 98888-7777");
        when(membros.editarConta(1L, 30L, dados, GERENTE_DA_MIDIA)).thenReturn(ANA);

        mvc.perform(editar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("sucesso", "Dados de Ana Souza salvos"));
    }

    @Test
    void dadosInvalidosVoltamComOsErrosNosCampos() throws Exception {
        when(membros.buscarParaEditar(1L, 30L, GERENTE_DA_MIDIA)).thenReturn(ANA_NA_MIDIA);

        String html = pagina(
                post("/ministerios/1/membros/30/editar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "")
                        .param("email", "sem-arroba")
                        .param("telefone", "abc"),
                MembroController.DADOS_DA_CONTA);

        assertThat(html)
                .contains("Informe o nome.", "Informe um e-mail válido", "Use só números")
                .contains("value=\"sem-arroba\"");
        verify(membros, never()).editarConta(anyLong(), anyLong(), any(), any());
    }

    @Test
    void emailDeOutraContaApareceNoCampo() throws Exception {
        when(membros.editarConta(eq(1L), eq(30L), any(), any()))
                .thenThrow(new RegraVioladaException("email", "O e-mail bia@x.com já é o login de outra conta."));

        assertThat(pagina(
                        post("/ministerios/1/membros/30/editar")
                                .with(user(GERENTE_DA_MIDIA))
                                .with(csrf())
                                .param("nome", "Ana Souza")
                                .param("email", "bia@x.com"),
                        MembroController.DADOS_DA_CONTA))
                .contains("id=\"email-erro\"", "O e-mail bia@x.com já é o login de outra conta.");
    }

    @Test
    void contaQueOGerenteNaoPodeEditarVoltaParaOMembroComOMotivo() throws Exception {
        var recusa = RegraVioladaException.geral("Os dados de Ana Souza não mudaram.");
        when(membros.buscarParaEditar(1L, 30L, GERENTE_DA_MIDIA)).thenThrow(recusa);
        when(membros.editarConta(eq(1L), eq(30L), any(), any())).thenThrow(recusa);

        mvc.perform(get("/ministerios/1/membros/30/editar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("recusa", "Os dados de Ana Souza não mudaram."));
        mvc.perform(editar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("recusa", "Os dados de Ana Souza não mudaram."));
        mvc.perform(post("/ministerios/1/membros/30/editar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", ""))
                .andExpect(redirectedUrl("/ministerios/1/membros/30"))
                .andExpect(flash().attribute("recusa", "Os dados de Ana Souza não mudaram."));
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/membros").with(user(GERENTE_DA_MIDIA)).param("nome", "Ana"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/senha")
                        .with(user(GERENTE_DA_MIDIA))
                        .param("senha", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/remover").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/membros/30/editar")
                        .with(user(GERENTE_DA_MIDIA))
                        .param("nome", "Ana Souza")
                        .param("email", "ana@x.com"))
                .andExpect(status().isForbidden());
        verify(membros, never()).editarConta(anyLong(), anyLong(), any(), any());
        mvc.perform(post("/ministerios/1/membros/30/desativar").with(user(ADMIN)))
                .andExpect(status().isForbidden());
        verify(membros, never()).desativarConta(anyLong(), anyLong(), any());
    }

    private static MockHttpServletRequestBuilder editar(long ministerioId) {
        return post("/ministerios/{m}/membros/30/editar", ministerioId)
                .with(csrf())
                .param("nome", "Ana Souza")
                .param("email", "ana.souza@x.com")
                .param("telefone", "(11) 98888-7777");
    }

    private static MockHttpServletRequestBuilder cadastrar(long ministerioId) {
        return post("/ministerios/{m}/membros", ministerioId)
                .with(csrf())
                .param("nome", "Ana Souza")
                .param("email", "ana@x.com")
                .param("telefone", "")
                .param("senhaProvisoria", "provisoria1");
    }

    private String pagina(MockHttpServletRequestBuilder requisicao, String visao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andExpect(view().name(visao))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }
}
