package br.igreja.escala.disponibilidade.web;

import static br.igreja.escala.AcessoDeTeste.ADMIN;
import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.DisponibilidadeDeUmMembro;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.disponibilidade.service.EventoDoPainel;
import br.igreja.escala.disponibilidade.service.GrupoDeDisponibilidade;
import br.igreja.escala.disponibilidade.service.LembreteDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.LinhaDeDisponibilidade;
import br.igreja.escala.disponibilidade.service.LinhaDoPainel;
import br.igreja.escala.disponibilidade.service.PainelDaDisponibilidade;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(PainelDaDisponibilidadeController.class)
class PainelDaDisponibilidadeControllerTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final UsuarioResumo ANA = new UsuarioResumo(30L, "Ana Souza", "ana@x.com", null, false, false, true);
    private static final UsuarioResumo BRUNO =
            new UsuarioResumo(31L, "Bruno Lima", "bruno@x.com", null, false, false, true);

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    ConsultaDaDisponibilidade consulta;

    @MockitoBean
    DisponibilidadeService disponibilidades;

    @MockitoBean
    LembreteDaDisponibilidade lembretes;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    EventoService eventos;

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(consulta.painel(1L, NOVEMBRO)).thenReturn(painel(false));
        when(consulta.peloGerente(1L, 30L, NOVEMBRO)).thenReturn(daAna(false));
        when(consulta.peloGerente(1L, 99L, NOVEMBRO)).thenThrow(new NaoEncontradoException("99 na Mídia"));
    }

    @Test
    void membroComumNaoVeNemTrava() throws Exception {
        mvc.perform(get("/ministerios/1/disponibilidade").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(travar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(destravar(1).with(user(MEMBRO))).andExpect(status().isForbidden());

        verify(disponibilidades, never()).travar(anyLong(), any(), any());
        verify(disponibilidades, never()).destravar(anyLong(), any(), anyBoolean(), any());
    }

    @Test
    void gerenteDaMidiaNaoVeNemTravaOLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/disponibilidade").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(travar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(destravar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());

        verify(disponibilidades, never()).travar(anyLong(), any(), any());
        verify(disponibilidades, never()).destravar(anyLong(), any(), anyBoolean(), any());
    }

    @Test
    void membroComumNaoMarcaPorOutroMembroNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/1/disponibilidade/membros/30").with(user(MEMBRO)))
                .andExpect(status().isForbidden());
        mvc.perform(marcarPor(1, 30, 500).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(marcarPor(1, MEMBRO.getId(), 500).with(user(MEMBRO))).andExpect(status().isForbidden());

        verify(disponibilidades, never()).marcarPeloGerente(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void gerenteDaMidiaNaoMarcaNoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/disponibilidade/membros/30").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(marcarPor(2, 30, 500).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());

        verify(disponibilidades, never()).marcarPeloGerente(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void membroOuEventoDeOutroMinisterioE404() throws Exception {
        doThrow(new NaoEncontradoException("99 na Mídia"))
                .when(disponibilidades)
                .marcarPeloGerente(1L, 99L, 500L, Resposta.PODE, GERENTE_DA_MIDIA);
        doThrow(new NaoEncontradoException("Evento 900 na Mídia"))
                .when(disponibilidades)
                .marcarPeloGerente(1L, 30L, 900L, Resposta.PODE, GERENTE_DA_MIDIA);

        mvc.perform(get("/ministerios/1/disponibilidade/membros/99").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
        mvc.perform(marcarPor(1, 99, 500).with(user(GERENTE_DA_MIDIA))).andExpect(status().isNotFound());
        mvc.perform(marcarPor(1, 30, 900).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginaDoMembroUsaOMesmoAvailabilityPickerEditavelMesmoTravado() throws Exception {
        when(consulta.peloGerente(1L, 30L, NOVEMBRO)).thenReturn(daAna(true));

        String html = mvc.perform(
                        get("/ministerios/1/disponibilidade/membros/30").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(PainelDaDisponibilidadeController.MEMBRO))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("<h1 class=\"text-title\">Mídia — Novembro</h1>", "Disponibilidade de Ana Souza")
                .contains("href=\"/ministerios/1/disponibilidade/membros/30?mes=2026-12\"")
                .contains("hx-post=\"/ministerios/1/disponibilidade/membros/30/eventos/500?mes=2026-11\"")
                .contains("Disponibilidade travada", "você ainda pode marcar", "1 de 1 respondido")
                .contains("href=\"/ministerios/1/disponibilidade?mes=2026-11\"", "Voltar ao painel")
                .doesNotContain("disabled", "rt-avail--locked");
    }

    @Test
    void toqueDoGerenteComHtmxDevolveOGrupoDaPessoa() throws Exception {
        String html = mvc.perform(
                        marcarPor(1, 30, 500).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(PainelDaDisponibilidadeController.RESPOSTA))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(disponibilidades).marcarPeloGerente(1L, 30L, 500L, Resposta.PODE, GERENTE_DA_MIDIA);
        assertThat(html.strip())
                .startsWith("<section id=\"grupo-1\"")
                .contains("hx-post=\"/ministerios/1/disponibilidade/membros/30/eventos/500?mes=2026-11\"")
                .doesNotContain("<html", "hx-swap-oob");
    }

    @Test
    void toqueDoGerenteSemJsVoltaParaAPaginaDoMembroComOMotivo() throws Exception {
        doThrow(RegraVioladaException.geral("01/11 · Culto da manhã já começou: não dá mais para marcar."))
                .when(disponibilidades)
                .marcarPeloGerente(1L, 30L, 500L, Resposta.PODE, GERENTE_DA_MIDIA);

        mvc.perform(marcarPor(1, 30, 500).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/disponibilidade/membros/30?mes=2026-11"))
                .andExpect(flash().attribute("recusa", "01/11 · Culto da manhã já começou: não dá mais para marcar."));
    }

    @Test
    void ministerioQueNaoExisteE404() throws Exception {
        mvc.perform(get("/ministerios/9/disponibilidade").with(user(ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void painelMostraQuemFaltaEAVisaoMembroPorEvento() throws Exception {
        String html = pagina(get("/ministerios/1/disponibilidade").with(user(GERENTE_DA_MIDIA)));

        assertThat(html)
                .contains("<h1 class=\"text-title\">Mídia — Novembro</h1>", "Novembro 2026", "Nov 2026")
                .contains("href=\"/ministerios/1/disponibilidade?mes=2026-10\"")
                .contains("<form id=\"travar-disponibilidade\" action=\"/ministerios/1/disponibilidade/travar\""
                        + " method=\"post\" hidden>")
                .contains("<input type=\"hidden\" name=\"mes\" value=\"2026-11\">")
                .contains("<button type=\"submit\" class=\"rt-btn rt-btn--dark\" form=\"travar-disponibilidade\">")
                .contains("Travar disponibilidade", "#lock\"", "Disponibilidade aberta")
                .doesNotContain("Destravar disponibilidade", "rt-btn--primary")
                .contains("<span class=\"rt-label\">Responderam</span>", "de 2 membros")
                .contains("Faltam responder", "1 sem nenhuma resposta", "aria-label=\"Ver Faltam responder\"")
                .contains("href=\"#respostas\"", "Eventos por vir", "em novembro")
                .contains("Faltam responder (1)", "Responderam (1)", "Pode em 1 de 2")
                .contains("<span>01 Dom</span> <span class=\"rt-caption\">09h30</span>")
                .contains("title=\"Culto da manhã\"", "<tr class=\"is-alert\">", "Sem resposta")
                .contains("<span class=\"sr-only\">Pode</span>", "<span class=\"sr-only\">Não pode</span>")
                .contains("<th scope=\"row\" colspan=\"2\">Podem</th>")
                .contains(
                        "aria-label=\"Marcar por Ana Souza\"",
                        "href=\"/ministerios/1/disponibilidade/membros/30?mes=2026-11\"")
                .contains("<a href=\"/ministerios/1/disponibilidade/membros/31?mes=2026-11\" class=\"rt-list-row\">");
    }

    @Test
    void copiarLembreteAbreOTextoParaOWhatsApp() throws Exception {
        when(lembretes.texto("Mídia", painel(false)))
                .thenReturn(
                        "*Mídia — Novembro*\nA disponibilidade de novembro está aberta.\n\nAinda faltam: Bruno Lima.");

        assertThat(pagina(get("/ministerios/1/disponibilidade").with(user(GERENTE_DA_MIDIA))))
                .contains("<button type=\"button\" class=\"rt-btn rt-btn--outline\" popovertarget=\"lembrete\">")
                .contains("Copiar lembrete", "<div id=\"lembrete\" popover class=\"rt-sheet")
                .contains("aria-label=\"Lembrete para o WhatsApp\"")
                .contains("<p id=\"texto-do-lembrete\" class=\"rt-panel whitespace-pre-line p-4 select-all\">*Mídia —"
                        + " Novembro* A disponibilidade de novembro está aberta. Ainda faltam: Bruno Lima.</p>")
                .contains("<template id=\"texto-do-lembrete-copiado\">", "Lembrete copiado")
                .contains("data-copiar=\"texto-do-lembrete\"", "Copiar texto");
    }

    @Test
    void travadoOfereceDestravarEExplicaQueOGerenteAindaMarca() throws Exception {
        when(consulta.painel(1L, NOVEMBRO)).thenReturn(painel(true));

        assertThat(pagina(get("/ministerios/1/disponibilidade").with(user(GERENTE_DA_MIDIA))))
                .contains(
                        "<button type=\"submit\" class=\"rt-btn rt-btn--outline\" form=\"destravar-disponibilidade\">")
                .contains("Destravar disponibilidade", "#lock-open\"", "Disponibilidade travada")
                .contains("Você ainda pode marcar em nome de alguém, e cada mudança fica na auditoria.")
                .doesNotContain("Travar disponibilidade", "Copiar lembrete");
        verify(lembretes, never()).texto(any(), any());
    }

    @Test
    void mesSemEventosNaoOfereceTravar() throws Exception {
        when(consulta.painel(1L, YearMonth.of(2026, 12)))
                .thenReturn(
                        new PainelDaDisponibilidade(YearMonth.of(2026, 12), false, false, false, List.of(), List.of()));

        assertThat(pagina(get("/ministerios/1/disponibilidade")
                        .param("mes", "2026-12")
                        .with(user(GERENTE_DA_MIDIA))))
                .contains("Nenhum evento por vir em dezembro.")
                .doesNotContain("Travar disponibilidade", "travar-disponibilidade", "rt-stats");
    }

    @Test
    void semNinguemHabilitadoAvisaOndeHabilitar() throws Exception {
        var semMembros = painel(false);
        when(consulta.painel(1L, NOVEMBRO))
                .thenReturn(new PainelDaDisponibilidade(NOVEMBRO, true, false, false, semMembros.eventos(), List.of()));

        assertThat(pagina(get("/ministerios/1/disponibilidade").with(user(GERENTE_DA_MIDIA))))
                .contains("Nenhum membro marca disponibilidade ainda", "href=\"/ministerios/1/membros\"")
                .doesNotContain("rt-stats");
    }

    @Test
    void travarEDestravarVoltamAoPainelComOResultado() throws Exception {
        when(disponibilidades.travar(1L, NOVEMBRO, GERENTE_DA_MIDIA)).thenReturn(true, false);
        when(disponibilidades.destravar(1L, NOVEMBRO, false, GERENTE_DA_MIDIA)).thenReturn(true, false);

        mvc.perform(travar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/disponibilidade?mes=2026-11"))
                .andExpect(flash().attribute("sucesso", "Disponibilidade de novembro travada"));
        mvc.perform(travar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "A disponibilidade de novembro já estava travada"));
        mvc.perform(destravar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Disponibilidade de novembro destravada"));
        mvc.perform(destravar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "A disponibilidade de novembro já estava aberta"));
    }

    @Test
    void comAEscalaPublicadaDestravarAbreAConfirmacao() throws Exception {
        when(consulta.painel(1L, NOVEMBRO)).thenReturn(painel(true, true));

        assertThat(pagina(get("/ministerios/1/disponibilidade").with(user(GERENTE_DA_MIDIA))))
                .contains("<button type=\"button\" class=\"rt-btn rt-btn--outline\" popovertarget=\"destravar-sheet\">")
                .contains("Destravar com a escala publicada?", "já está publicada")
                .contains("action=\"/ministerios/1/disponibilidade/destravar?mes=2026-11&amp;confirmado=true\"");
    }

    @Test
    void destravarConfirmadoPassaAConfirmacaoESemElaVoltaComOMotivo() throws Exception {
        when(disponibilidades.destravar(1L, NOVEMBRO, true, GERENTE_DA_MIDIA)).thenReturn(true);
        when(disponibilidades.destravar(1L, NOVEMBRO, false, GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("A escala de novembro está publicada."));

        mvc.perform(destravar(1).param("confirmado", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Disponibilidade de novembro destravada"));
        mvc.perform(destravar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("recusa", "A escala de novembro está publicada."));
    }

    @Test
    void travarMesSemEventosVoltaComOMotivo() throws Exception {
        when(disponibilidades.travar(1L, NOVEMBRO, GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("Novembro 2026 ainda não tem eventos."));

        mvc.perform(travar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/disponibilidade?mes=2026-11"))
                .andExpect(flash().attribute("recusa", "Novembro 2026 ainda não tem eventos."));
    }

    @Test
    void travarSemCsrfNaoPassa() throws Exception {
        mvc.perform(post("/ministerios/1/disponibilidade/travar")
                        .param("mes", "2026-11")
                        .with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());

        verify(disponibilidades, never()).travar(anyLong(), any(), any());
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andExpect(view().name(PainelDaDisponibilidadeController.PAINEL))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    private static MockHttpServletRequestBuilder travar(long ministerioId) {
        return post("/ministerios/{m}/disponibilidade/travar", ministerioId)
                .param("mes", "2026-11")
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder destravar(long ministerioId) {
        return post("/ministerios/{m}/disponibilidade/destravar", ministerioId)
                .param("mes", "2026-11")
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder marcarPor(long ministerioId, long usuarioId, long eventoId) {
        return post("/ministerios/{m}/disponibilidade/membros/{u}/eventos/{e}", ministerioId, usuarioId, eventoId)
                .param("resposta", "PODE")
                .param("mes", "2026-11")
                .with(csrf());
    }

    private static DisponibilidadeDeUmMembro daAna(boolean travado) {
        var manha = new LinhaDeDisponibilidade(
                500L, "Culto da manhã", "01", "Dom", "01/11 · Dom", "09h30", Resposta.PODE, null, null);
        return new DisponibilidadeDeUmMembro(
                ANA, new GrupoDeDisponibilidade(1L, "Mídia", "mint", NOVEMBRO, travado, true, List.of(manha)));
    }

    private static PainelDaDisponibilidade painel(boolean travado) {
        return painel(travado, false);
    }

    private static PainelDaDisponibilidade painel(boolean travado, boolean escalaPublicada) {
        return new PainelDaDisponibilidade(
                NOVEMBRO,
                true,
                travado,
                escalaPublicada,
                List.of(
                        new EventoDoPainel(500L, "Culto da manhã", "01 Dom", "09h30", 1),
                        new EventoDoPainel(501L, "Culto de domingo", "01 Dom", "18h00", 0)),
                List.of(
                        new LinhaDoPainel(BRUNO, Arrays.asList(null, null)),
                        new LinhaDoPainel(ANA, List.of(Resposta.PODE, Resposta.NAO_PODE))));
    }
}
