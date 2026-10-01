package br.igreja.escala.ministerio.web;

import static br.igreja.escala.AcessoDeTeste.ADMIN;
import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.DadosDaFuncao;
import br.igreja.escala.ministerio.service.FuncaoResumo;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelResumo;
import br.igreja.escala.ministerio.service.NivelService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(FuncaoController.class)
class FuncaoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    FuncaoService funcoes;

    @MockitoBean
    NivelService niveis;

    private final Ministerio midia = Exemplos.midia();

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(midia);
        when(ministerios.buscar(AcessoDeTeste.LOUVOR)).thenReturn(Exemplos.louvor());
    }

    @Test
    void membroComumNaoAbreNemAlteraAsFuncoes() throws Exception {
        mvc.perform(get("/ministerios/1/funcoes").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/1/funcoes/nova").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(criar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/funcoes/100/excluir")
                        .with(user(MEMBRO))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(funcoes, never()).criar(anyLong(), any());
        verify(funcoes, never()).excluir(anyLong(), anyLong());
    }

    @Test
    void gerenteDaMidiaNaoMexeNasFuncoesDoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/funcoes").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(criar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/funcoes/100")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Projeção")
                        .param("icone", "MONITOR")
                        .param("qtdMin", "1")
                        .param("qtdMax", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/funcoes/100/excluir")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(funcoes, never()).criar(anyLong(), any());
        verify(funcoes, never()).alterar(anyLong(), anyLong(), any());
        verify(funcoes, never()).excluir(anyLong(), anyLong());
    }

    @Test
    void adminAbreAsFuncoesDeQualquerMinisterio() throws Exception {
        when(funcoes.resumos(2L)).thenReturn(List.of());
        when(niveis.resumos(2L)).thenReturn(List.of());

        assertThat(pagina(get("/ministerios/2/funcoes").with(user(ADMIN)), FuncaoController.PAGINA))
                .contains("Louvor — Funções");
    }

    @Test
    void paginaListaFuncoesENiveisNoCelularENoDesktop() throws Exception {
        when(funcoes.resumos(1L)).thenReturn(List.of(new FuncaoResumo(100L, "Projeção", Icone.MONITOR, 1, 1, 12)));
        when(niveis.resumos(1L)).thenReturn(List.of(new NivelResumo(200L, "Iniciante", 1, 5)));

        String html = pagina(get("/ministerios/1/funcoes").with(user(GERENTE_DA_MIDIA)), FuncaoController.PAGINA);

        assertThat(html)
                .contains("<h1 class=\"text-title\">Mídia — Funções</h1>")
                .contains("1 pessoa por evento · 12 habilitados", "href=\"/ministerios/1/funcoes/100\"")
                .contains("Ordem 1 · 5 habilitações", "href=\"/ministerios/1/funcoes/niveis/200\"")
                .contains("aria-label=\"Editar Projeção\"", "aria-label=\"Editar Iniciante\"")
                .contains("class=\"rt-btn rt-btn--secondary\"", "Criar nível");
    }

    @Test
    void semFuncoesNemNiveisSugereOsPrimeiros() throws Exception {
        when(funcoes.resumos(1L)).thenReturn(List.of());
        when(niveis.resumos(1L)).thenReturn(List.of());

        assertThat(pagina(get("/ministerios/1/funcoes").with(user(GERENTE_DA_MIDIA)), FuncaoController.PAGINA))
                .contains("Nenhuma função ainda.", "Nenhum nível ainda.")
                .doesNotContain("rt-table");
    }

    @Test
    void formularioNovoJaVemComUmaPessoaPorEvento() throws Exception {
        String html = pagina(get("/ministerios/1/funcoes/nova").with(user(GERENTE_DA_MIDIA)), "ministerio/funcao-form");

        assertThat(html)
                .contains("action=\"/ministerios/1/funcoes\"", "Criar função")
                .contains(
                        "name=\"qtdMin\" type=\"number\"",
                        "min=\"0\" max=\"20\"",
                        "aria-describedby=\"qtdMin-dica\" value=\"1\">")
                .doesNotContain("excluir-funcao");
    }

    @Test
    void criaEVoltaParaAPaginaComToast() throws Exception {
        when(funcoes.criar(1L, new DadosDaFuncao("Projeção", Icone.MONITOR, 1, 1)))
                .thenReturn(Exemplos.projecao(midia));

        mvc.perform(criar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/funcoes"))
                .andExpect(flash().attribute("sucesso", "Função Projeção criada"));
    }

    @Test
    void formularioIncompletoVoltaComOsErrosNosCampos() throws Exception {
        String html = pagina(
                post("/ministerios/1/funcoes")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "")
                        .param("qtdMin", "")
                        .param("qtdMax", "50"),
                "ministerio/funcao-form");

        assertThat(html)
                .contains("Informe o nome da função.", "Escolha o ícone.")
                .contains("Informe quantas pessoas a função pede no mínimo.", "O máximo vai de 1 a 20.");
        verify(funcoes, never()).criar(anyLong(), any());
    }

    @Test
    void regraDoServicoApareceNoCampo() throws Exception {
        when(funcoes.criar(any(), any()))
                .thenThrow(new RegraVioladaException("qtdMax", "O máximo precisa ser igual ou maior que o mínimo."));

        assertThat(pagina(criar(1).with(user(GERENTE_DA_MIDIA)), "ministerio/funcao-form"))
                .contains("id=\"qtdMax-erro\"", "O máximo precisa ser igual ou maior que o mínimo.");
    }

    @Test
    void edicaoTemOsDadosAtuaisEAConfirmacaoDeExclusao() throws Exception {
        when(funcoes.buscar(1L, 100L)).thenReturn(Exemplos.projecao(midia));

        String html = pagina(get("/ministerios/1/funcoes/100").with(user(GERENTE_DA_MIDIA)), "ministerio/funcao-form");

        assertThat(html)
                .contains("action=\"/ministerios/1/funcoes/100\"", "value=\"Projeção\"")
                .contains("popovertarget=\"excluir-funcao\"", "id=\"excluir-funcao\" popover")
                .contains("action=\"/ministerios/1/funcoes/100/excluir\"", "Excluir Projeção?");
    }

    @Test
    void funcaoDeOutroMinisterioNaRotaDaMidiaE404() throws Exception {
        when(funcoes.buscar(1L, 999L)).thenThrow(new NaoEncontradoException("Função 999"));

        mvc.perform(get("/ministerios/1/funcoes/999").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void exclusaoRecusadaVoltaParaAEdicaoComOMotivo() throws Exception {
        when(funcoes.excluir(1L, 100L))
                .thenThrow(RegraVioladaException.geral("Projeção não foi excluída: 3 membros estão habilitados nela."));
        when(funcoes.buscar(1L, 100L)).thenReturn(Exemplos.projecao(midia));

        mvc.perform(post("/ministerios/1/funcoes/100/excluir")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/funcoes/100"))
                .andExpect(flash().attribute("recusa", "Projeção não foi excluída: 3 membros estão habilitados nela."));
        assertThat(pagina(
                        get("/ministerios/1/funcoes/100")
                                .with(user(GERENTE_DA_MIDIA))
                                .flashAttr("recusa", "Projeção não foi excluída: 3 membros estão habilitados nela."),
                        "ministerio/funcao-form"))
                .contains("class=\"rt-alert\" role=\"alert\"", "3 membros estão habilitados nela.");
    }

    @Test
    void excluiEVoltaParaAPagina() throws Exception {
        when(funcoes.excluir(1L, 100L)).thenReturn(Exemplos.projecao(midia));

        mvc.perform(post("/ministerios/1/funcoes/100/excluir")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/funcoes"))
                .andExpect(flash().attribute("sucesso", "Função Projeção excluída"));
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/funcoes").with(user(GERENTE_DA_MIDIA)).param("nome", "Projeção"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/funcoes/100/excluir").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder criar(long ministerioId) {
        return post("/ministerios/{m}/funcoes", ministerioId)
                .with(csrf())
                .param("nome", "Projeção")
                .param("icone", "MONITOR")
                .param("qtdMin", "1")
                .param("qtdMax", "1");
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
