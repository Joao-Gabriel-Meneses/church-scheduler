package br.igreja.escala.ministerio.web;

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
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.DadosDoNivel;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(NivelController.class)
class NivelControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    NivelService niveis;

    private final Ministerio midia = Exemplos.midia();

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(midia);
    }

    @Test
    void soOGerenteDoMinisterioCriaAlteraOuExcluiNiveis() throws Exception {
        mvc.perform(get("/ministerios/1/funcoes/niveis/novo").with(user(MEMBRO)))
                .andExpect(status().isForbidden());
        mvc.perform(criar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(criar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/funcoes/niveis/200/excluir")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(niveis, never()).criar(anyLong(), any());
        verify(niveis, never()).excluir(anyLong(), anyLong());
    }

    @Test
    void novoSugereAProximaOrdem() throws Exception {
        when(niveis.proximaOrdem(1L)).thenReturn(3);

        assertThat(pagina(get("/ministerios/1/funcoes/niveis/novo").with(user(GERENTE_DA_MIDIA))))
                .contains(
                        "action=\"/ministerios/1/funcoes/niveis\"",
                        "name=\"ordem\" type=\"number\"",
                        "aria-describedby=\"ordem-dica\" value=\"3\">");
    }

    @Test
    void criaEVoltaParaAPaginaDeFuncoes() throws Exception {
        when(niveis.criar(1L, new DadosDoNivel("Iniciante", 1))).thenReturn(Exemplos.iniciante(midia));

        mvc.perform(criar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/funcoes"))
                .andExpect(flash().attribute("sucesso", "Nível Iniciante criado"));
    }

    @Test
    void ordemRepetidaApareceNoCampoOrdem() throws Exception {
        when(niveis.criar(any(), any()))
                .thenThrow(new RegraVioladaException("ordem", "A ordem 1 já é do nível Iniciante."));

        assertThat(pagina(criar(1).with(user(GERENTE_DA_MIDIA))))
                .contains("id=\"ordem-erro\"", "A ordem 1 já é do nível Iniciante.");
    }

    @Test
    void ordemForaDoIntervaloVoltaComErro() throws Exception {
        assertThat(pagina(post("/ministerios/1/funcoes/niveis")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Iniciante")
                        .param("ordem", "0")))
                .contains("A ordem começa em 1.");
    }

    @Test
    void edicaoESalvamento() throws Exception {
        when(niveis.buscar(1L, 200L)).thenReturn(Exemplos.iniciante(midia));
        when(niveis.alterar(1L, 200L, new DadosDoNivel("Novato", 1))).thenReturn(Exemplos.iniciante(midia));

        assertThat(pagina(get("/ministerios/1/funcoes/niveis/200").with(user(GERENTE_DA_MIDIA))))
                .contains("value=\"Iniciante\"", "action=\"/ministerios/1/funcoes/niveis/200/excluir\"");
        mvc.perform(post("/ministerios/1/funcoes/niveis/200")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Novato")
                        .param("ordem", "1"))
                .andExpect(redirectedUrl("/ministerios/1/funcoes"));
    }

    @Test
    void nivelDeOutroMinisterioE404() throws Exception {
        when(niveis.buscar(1L, 999L)).thenThrow(new NaoEncontradoException("Nível 999"));

        mvc.perform(get("/ministerios/1/funcoes/niveis/999").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void exclusaoRecusadaVoltaParaAEdicao() throws Exception {
        when(niveis.excluir(1L, 200L)).thenThrow(RegraVioladaException.geral("Iniciante não foi excluído."));

        mvc.perform(post("/ministerios/1/funcoes/niveis/200/excluir")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/1/funcoes/niveis/200"))
                .andExpect(flash().attribute("recusa", "Iniciante não foi excluído."));
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/funcoes/niveis")
                        .with(user(GERENTE_DA_MIDIA))
                        .param("nome", "Iniciante"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder criar(long ministerioId) {
        return post("/ministerios/{m}/funcoes/niveis", ministerioId)
                .with(csrf())
                .param("nome", "Iniciante")
                .param("ordem", "1");
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andExpect(view().name("ministerio/nivel-form"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }
}
