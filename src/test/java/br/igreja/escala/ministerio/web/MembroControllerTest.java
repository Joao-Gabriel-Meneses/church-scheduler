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
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.DadosDoMembro;
import br.igreja.escala.ministerio.service.MembroResumo;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.ResultadoDoCadastro;
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
            new UsuarioResumo(30L, "Ana Souza", "ana@x.com", "(11) 98888-7777", false, true);
    private static final MembroResumo ANA_NA_MIDIA = new MembroResumo(ANA, false, List.of("Projeção · Experiente"));

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    MembroService membros;

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
    void listaNoCelularENoDesktopComHabilitacoesEAcesso() throws Exception {
        var gerente = new MembroResumo(
                new UsuarioResumo(10L, "Gerente da Mídia", "g@x.com", null, false, false), true, List.of());
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
                .contains("Projeção · Experiente", "ana@x.com")
                .contains("popovertarget=\"redefinir-senha\"", "action=\"/ministerios/1/membros/30/senha\"")
                .contains("minlength=\"8\"", "maxlength=\"64\"")
                .contains("popovertarget=\"remover-membro\"", "action=\"/ministerios/1/membros/30/remover\"")
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
                .contains("só o administrador redefine")
                .doesNotContain("id=\"redefinir-senha\"", "id=\"remover-membro\"");
    }

    @Test
    void naPropriaPaginaOGerenteNaoSeRemoveNemRedefineASenha() throws Exception {
        var proprio = new MembroResumo(
                new UsuarioResumo(10L, "Gerente da Mídia", "g@x.com", null, false, false), true, List.of());
        when(membros.buscar(1L, 10L)).thenReturn(proprio);
        when(membros.podeMexerNaConta(eq(proprio), any())).thenReturn(true);

        assertThat(pagina(get("/ministerios/1/membros/10").with(user(ADMIN)), MembroController.PAGINA_DO_MEMBRO))
                .contains("id=\"redefinir-senha\"");
        assertThat(pagina(
                        get("/ministerios/1/membros/10").with(user(GERENTE_DA_MIDIA)),
                        MembroController.PAGINA_DO_MEMBRO))
                .contains("Esta é a sua conta.")
                .doesNotContain("id=\"redefinir-senha\"", "id=\"remover-membro\"");
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
    void pessoaDeOutroMinisterioE404() throws Exception {
        when(membros.buscar(1L, 99L)).thenThrow(new NaoEncontradoException("Usuário 99"));

        mvc.perform(get("/ministerios/1/membros/99").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
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
