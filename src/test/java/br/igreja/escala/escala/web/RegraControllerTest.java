package br.igreja.escala.escala.web;

import static br.igreja.escala.AcessoDeTeste.ADMIN;
import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
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
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.service.RegraResumo;
import br.igreja.escala.escala.service.RegraService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(RegraController.class)
class RegraControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    RegraService regras;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    NivelService niveis;

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        var midia = Exemplos.midia();
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(midia);
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(niveis.listar(1L)).thenReturn(List.of(Exemplos.iniciante(midia), Exemplos.experiente(midia)));
        when(regras.doMinisterio(1L)).thenReturn(RegrasDoMinisterio.padrao());
        when(regras.resumos(1L))
                .thenReturn(List.of(
                        new RegraResumo(
                                "HABILITACAO",
                                "Habilitação",
                                "Só serve numa função quem está habilitado nela.",
                                "Rígida",
                                "Sempre ativa",
                                true,
                                null),
                        new RegraResumo(
                                "LIMITE_POR_PERIODO",
                                "Limite do mês",
                                "Cada pessoa serve em no máximo 3 eventos no mês.",
                                "Rígida",
                                "Ligada",
                                true,
                                "limite"),
                        new RegraResumo(
                                "MIN_POR_NIVEL_NO_EVENTO",
                                "Mínimo por nível",
                                "Todo evento com alguém escalado tem pelo menos esse número de pessoas do nível.",
                                "Rígida",
                                "Desligada",
                                false,
                                "minimo-por-nivel")));
    }

    @Test
    void membroComumNaoVeNemMudaAsRegras() throws Exception {
        mvc.perform(get("/ministerios/1/regras").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/1/regras/limite").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(limite(1, "4").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(minimo(1, "true", "200", "1").with(user(MEMBRO))).andExpect(status().isForbidden());
        verify(regras, never()).alterarLimite(anyLong(), anyInt(), any());
        verify(regras, never()).alterarMinimoPorNivel(anyLong(), anyBoolean(), any(), anyInt(), any());
    }

    @Test
    void gerenteDaMidiaNaoMudaAsRegrasDoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/regras").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/2/regras/minimo-por-nivel").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(limite(2, "4").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(minimo(2, "true", "200", "1").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        verify(regras, never()).alterarLimite(anyLong(), anyInt(), any());
        verify(regras, never()).alterarMinimoPorNivel(anyLong(), anyBoolean(), any(), anyInt(), any());
    }

    @Test
    void ministerioQueNaoExisteENivelDeOutroMinisterioSao404() throws Exception {
        mvc.perform(get("/ministerios/9/regras").with(user(ADMIN))).andExpect(status().isNotFound());
        when(regras.alterarMinimoPorNivel(1L, true, 900L, 1, GERENTE_DA_MIDIA))
                .thenThrow(new NaoEncontradoException("Nível 900"));
        mvc.perform(minimo(1, "true", "900", "1").with(user(GERENTE_DA_MIDIA))).andExpect(status().isNotFound());
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/regras/limite").param("maximo", "4").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        verify(regras, never()).alterarLimite(anyLong(), anyInt(), any());
    }

    @Test
    void listaMostraCadaRegraComRigidezEstadoEOsAtalhosDeEdicao() throws Exception {
        assertThat(pagina(get("/ministerios/1/regras").with(user(GERENTE_DA_MIDIA)), RegraController.LISTA))
                .contains("<h1 class=\"text-title\">Mídia — Regras</h1>")
                .contains("Habilitação", "HABILITACAO", "Sempre ativa", "Rígida")
                .contains("Limite do mês", "Cada pessoa serve em no máximo 3 eventos no mês.")
                .contains("href=\"/ministerios/1/regras/limite\"", "href=\"/ministerios/1/regras/minimo-por-nivel\"")
                .contains("aria-label=\"Editar Limite do mês\"", "Desligada")
                .doesNotContain("rt-btn--primary", "href=\"/ministerios/1/regras/null\"");
    }

    @Test
    void formularioDoLimiteVemComOValorAtual() throws Exception {
        assertThat(pagina(get("/ministerios/1/regras/limite").with(user(GERENTE_DA_MIDIA)), RegraController.LIMITE))
                .contains("Limite do mês", "Eventos por pessoa no mês", "name=\"maximo\"", "value=\"3\"")
                .contains("action=\"/ministerios/1/regras/limite\"");
    }

    @Test
    void salvaOLimiteEAvisa() throws Exception {
        when(regras.alterarLimite(1L, 4, GERENTE_DA_MIDIA)).thenReturn(true, false);

        mvc.perform(limite(1, "4").with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/regras"))
                .andExpect(flash().attribute("sucesso", "Limite do mês salvo: 4 por pessoa"));
        mvc.perform(limite(1, "4").with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "O limite do mês já era 4"));
    }

    @Test
    void limiteForaDoIntervaloVoltaComOErroNoCampo() throws Exception {
        assertThat(pagina(limite(1, "0").with(user(GERENTE_DA_MIDIA)), RegraController.LIMITE))
                .contains("id=\"maximo-erro\"", "O limite vai de 1 a 31 eventos no mês.");
        verify(regras, never()).alterarLimite(anyLong(), anyInt(), any());
    }

    @Test
    void formularioDoMinimoPorNivelTemOsNiveisDoMinisterio() throws Exception {
        assertThat(pagina(
                        get("/ministerios/1/regras/minimo-por-nivel").with(user(GERENTE_DA_MIDIA)),
                        RegraController.MINIMO_POR_NIVEL))
                .contains("Ligar o mínimo por nível", "name=\"ligada\"", "name=\"_ligada\"")
                .contains("<option value=\"\">Escolha o nível</option>")
                .contains("<option value=\"200\">Iniciante</option>", "<option value=\"201\">Experiente</option>")
                .contains("name=\"minimo\"", "value=\"1\"");
    }

    @Test
    void ligarSemNivelVoltaComOErroNoCampo() throws Exception {
        when(regras.alterarMinimoPorNivel(1L, true, null, 1, GERENTE_DA_MIDIA))
                .thenThrow(new RegraVioladaException("nivelId", "Escolha o nível."));

        assertThat(pagina(minimo(1, "true", "", "1").with(user(GERENTE_DA_MIDIA)), RegraController.MINIMO_POR_NIVEL))
                .contains("id=\"nivelId-erro\"", "Escolha o nível.");
    }

    @Test
    void ligaODesligaOMinimoPorNivel() throws Exception {
        when(regras.alterarMinimoPorNivel(1L, true, 200L, 1, GERENTE_DA_MIDIA)).thenReturn(true);
        when(regras.alterarMinimoPorNivel(1L, false, 200L, 1, GERENTE_DA_MIDIA)).thenReturn(true);

        mvc.perform(minimo(1, "true", "200", "1").with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/regras"))
                .andExpect(flash().attribute("sucesso", "Mínimo por nível ligado"));
        mvc.perform(post("/ministerios/1/regras/minimo-por-nivel")
                        .param("_ligada", "on")
                        .param("nivelId", "200")
                        .param("minimo", "1")
                        .with(csrf())
                        .with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Mínimo por nível desligado"));
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

    private static MockHttpServletRequestBuilder limite(long ministerioId, String maximo) {
        return post("/ministerios/{m}/regras/limite", ministerioId)
                .param("maximo", maximo)
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder minimo(
            long ministerioId, String ligada, String nivelId, String minimo) {
        return post("/ministerios/{m}/regras/minimo-por-nivel", ministerioId)
                .param("ligada", ligada)
                .param("_ligada", "on")
                .param("nivelId", nivelId)
                .param("minimo", minimo)
                .with(csrf());
    }
}
