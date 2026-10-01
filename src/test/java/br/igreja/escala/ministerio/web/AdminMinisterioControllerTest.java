package br.igreja.escala.ministerio.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import br.igreja.escala.Pessoas;
import br.igreja.escala.TesteDeController;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.DadosDoMinisterio;
import br.igreja.escala.ministerio.service.MinisterioResumo;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeController(AdminMinisterioController.class)
class AdminMinisterioControllerTest {

    private static final UsuarioAutenticado ADMIN = Pessoas.admin(1L, "Admin");
    private static final UsuarioAutenticado GERENTE = Pessoas.membro(10L, "Gerente");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService detalhes;

    @MockitoBean
    MinisterioService ministerios;

    @Test
    void soOAdminAbreOuAlteraMinisterios() throws Exception {
        mvc.perform(get("/admin/ministerios").with(user(GERENTE))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/ministerios/novo").with(user(GERENTE))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/ministerios")
                        .with(user(GERENTE))
                        .with(csrf())
                        .param("nome", "Mídia")
                        .param("cor", "MINT")
                        .param("icone", "MONITOR"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/ministerios")).andExpect(redirectedUrl("/login"));
        verify(ministerios, never()).criar(any());
    }

    @Test
    void listaComGerentesEMembrosNoCelularENoDesktop() throws Exception {
        when(ministerios.resumos())
                .thenReturn(List.of(new MinisterioResumo(
                        1L, "Mídia", CorDoMinisterio.MINT, Icone.MONITOR, List.of("Ana Souza"), 18)));

        String html = pagina(get("/admin/ministerios").with(user(ADMIN)), "ministerio/ministerios");

        assertThat(html)
                .contains("<ul class=\"rt-list md:hidden\">", "Gerente: Ana Souza · 18 membros")
                .contains("<div class=\"rt-panel hidden overflow-x-auto md:block\">")
                .contains("class=\"rt-badge bg-tint-mint\"", "href=\"/admin/ministerios/1\"")
                .contains("aria-label=\"Editar Mídia\"", "Criar ministério");
    }

    /** O layout usa ${ministerios} para a SideRail; a lista da página não pode ocupar esse nome. */
    @Test
    void listaComVariosMinisteriosNaoSeConfundeComASideRail() throws Exception {
        when(ministerios.resumos())
                .thenReturn(List.of(
                        new MinisterioResumo(2L, "Louvor", CorDoMinisterio.ROSE, Icone.MUSIC, List.of(), 0),
                        new MinisterioResumo(1L, "Mídia", CorDoMinisterio.MINT, Icone.MONITOR, List.of(), 3)));

        assertThat(pagina(get("/admin/ministerios").with(user(ADMIN)), "ministerio/ministerios"))
                .contains("Louvor", "Mídia", "Sem gerente · 0 membros")
                .doesNotContain("aria-label=\"Ministérios\"");
    }

    @Test
    void listaVaziaConvidaACriarOPrimeiro() throws Exception {
        when(ministerios.resumos()).thenReturn(List.of());

        assertThat(pagina(get("/admin/ministerios").with(user(ADMIN)), "ministerio/ministerios"))
                .contains("Nenhum ministério ainda.")
                .doesNotContain("rt-table");
    }

    @Test
    void criaEVoltaParaAListaComToast() throws Exception {
        when(ministerios.criar(new DadosDoMinisterio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR)))
                .thenReturn(new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR));

        mvc.perform(post("/admin/ministerios")
                        .with(user(ADMIN))
                        .with(csrf())
                        .param("nome", "Mídia")
                        .param("cor", "MINT")
                        .param("icone", "MONITOR"))
                .andExpect(redirectedUrl("/admin/ministerios"))
                .andExpect(flash().attribute("sucesso", "Ministério Mídia criado"));
    }

    @Test
    void formularioIncompletoVoltaComOsErrosNosCampos() throws Exception {
        String html = pagina(
                post("/admin/ministerios").with(user(ADMIN)).with(csrf()).param("nome", " "),
                "ministerio/ministerio-form");

        assertThat(html)
                .contains("Informe o nome do ministério.", "Escolha a cor da etiqueta.", "Escolha o ícone.")
                .contains("<option value=\"\">Escolha a cor</option>");
        verify(ministerios, never()).criar(any());
    }

    @Test
    void nomeEmUsoApareceNoCampoNome() throws Exception {
        when(ministerios.criar(any()))
                .thenThrow(new RegraVioladaException("nome", "Já existe um ministério chamado Mídia."));

        String html = pagina(
                post("/admin/ministerios")
                        .with(user(ADMIN))
                        .with(csrf())
                        .param("nome", "Mídia")
                        .param("cor", "MINT")
                        .param("icone", "MONITOR"),
                "ministerio/ministerio-form");

        assertThat(html).contains("id=\"nome-erro\"", "Já existe um ministério chamado Mídia.");
    }

    @Test
    void editaComOsDadosAtuaisESalva() throws Exception {
        var midia = new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR);
        ReflectionTestUtils.setField(midia, "id", 1L);
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(ministerios.alterar(any(), any())).thenReturn(midia);

        assertThat(pagina(get("/admin/ministerios/1").with(user(ADMIN)), "ministerio/ministerio-form"))
                .contains("action=\"/admin/ministerios/1\"", "value=\"Mídia\"")
                .contains("<option value=\"MINT\" selected=\"selected\">Verde</option>");
        mvc.perform(post("/admin/ministerios/1")
                        .with(user(ADMIN))
                        .with(csrf())
                        .param("nome", "Mídia")
                        .param("cor", "ROSE")
                        .param("icone", "VIDEO"))
                .andExpect(redirectedUrl("/admin/ministerios"))
                .andExpect(flash().attribute("sucesso", "Ministério Mídia salvo"));
    }

    @Test
    void ministerioQueNaoExisteE404() throws Exception {
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));

        mvc.perform(get("/admin/ministerios/9").with(user(ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/admin/ministerios").with(user(ADMIN)).param("nome", "Mídia"))
                .andExpect(status().isForbidden());
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
