package br.igreja.escala.disponibilidade.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

import br.igreja.escala.Pessoas;
import br.igreja.escala.TesteDeController;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.disponibilidade.service.GrupoDeDisponibilidade;
import br.igreja.escala.disponibilidade.service.LinhaDeDisponibilidade;
import br.igreja.escala.disponibilidade.service.TelaDaDisponibilidade;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeController(DisponibilidadeController.class)
class DisponibilidadeControllerTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final UsuarioAutenticado ANA = Pessoas.membro(30L, "Ana Souza");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @MockitoBean
    ConsultaDaDisponibilidade consulta;

    @MockitoBean
    DisponibilidadeService disponibilidades;

    @MockitoBean
    EventoService eventos;

    private final LinhaDeDisponibilidade manha = new LinhaDeDisponibilidade(
            500L, "Culto da manhã", "01", "Dom", "01/11 · Dom", "09h30", Resposta.PODE, null, null);
    private final LinhaDeDisponibilidade noite = new LinhaDeDisponibilidade(
            501L, "Culto de domingo", "01", "Dom", "01/11 · Dom", "18h00", null, "Paula Ribeiro", null);
    private final LinhaDeDisponibilidade ensaio = new LinhaDeDisponibilidade(
            600L, "Ensaio do louvor", "07", "Sáb", "07/11 · Sáb", "16h00", Resposta.NAO_PODE, null, null);

    @BeforeEach
    void prepara() {
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(consulta.doMembro(30L, NOVEMBRO)).thenReturn(tela(midia(false), louvor(true)));
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/disponibilidade")).andExpect(redirectedUrl("/login"));
        mvc.perform(marcar(500L, "PODE")).andExpect(redirectedUrl("/login"));
        verify(disponibilidades, never()).marcar(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void abreNoProximoMesComUmGrupoPorMinisterio() throws Exception {
        String html = mvc.perform(get("/disponibilidade").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(view().name(DisponibilidadeController.TELA))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("<h1 class=\"text-title\">Disponibilidade — Novembro</h1>")
                .contains("href=\"/disponibilidade?mes=2026-10\"", "href=\"/disponibilidade?mes=2026-12\"")
                .contains("Toque em Pode ou Não pode em cada evento. Sem resposta conta como não pode.")
                .contains("<p id=\"respondidos\" class=\"rt-label\" aria-live=\"polite\">2 de 3 respondidos</p>")
                .contains("<section id=\"grupo-1\"", "class=\"rt-badge bg-tint-mint\"", "1 de 2 respondidos")
                .contains("<section id=\"grupo-2\"", "class=\"rt-badge bg-tint-rose\"")
                .contains("hx-post=\"/disponibilidade/ministerios/1/eventos/500?mes=2026-11\"")
                .contains("Culto da manhã", "Culto de domingo", "Marcado por Paula Ribeiro")
                .doesNotContain("PREFERE_NAO", "Prefiro não", "hx-swap-oob");
        String louvor = html.substring(html.indexOf("<section id=\"grupo-2\""));
        assertThat(louvor)
                .contains("class=\"rt-avail rt-avail--locked\"", "Disponibilidade travada")
                .contains("O gerente travou a disponibilidade deste mês.")
                .contains("disabled=\"disabled\"");
        String midia = html.substring(html.indexOf("<section id=\"grupo-1\""), html.indexOf("<section id=\"grupo-2\""));
        assertThat(midia).doesNotContain("rt-avail--locked", "disabled", "Disponibilidade travada");
    }

    @Test
    void comUmMinisterioSoOTituloEDeleESemEtiqueta() throws Exception {
        when(consulta.doMembro(30L, YearMonth.of(2026, 12))).thenReturn(tela(midia(false)));

        assertThat(pagina(get("/disponibilidade").param("mes", "2026-12").with(user(ANA))))
                .contains("<h1 class=\"text-title\">Mídia — Novembro</h1>")
                .doesNotContain("bg-tint-mint");
    }

    @Test
    void quemNaoServeEmNenhumMinisterioEntendeOPorque() throws Exception {
        when(consulta.doMembro(30L, NOVEMBRO)).thenReturn(new TelaDaDisponibilidade(NOVEMBRO, false, List.of()));

        assertThat(pagina(get("/disponibilidade").with(user(ANA))))
                .contains("Você ainda não tem função em nenhum ministério.", "0 de 0 respondidos");
    }

    @Test
    void toqueComHtmxGravaParaQuemEstaLogadoEDevolveOGrupoEOTotal() throws Exception {
        String html = mvc.perform(
                        marcar(500L, "NAO_PODE").header("HX-Request", "true").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(view().name(DisponibilidadeController.RESPOSTA))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        verify(disponibilidades).marcar(30L, 1L, 500L, Resposta.NAO_PODE);
        assertThat(html.strip())
                .startsWith("<section id=\"grupo-1\"")
                .contains("<p id=\"respondidos\" class=\"rt-label\" aria-live=\"polite\" hx-swap-oob=\"true\">")
                .doesNotContain("<html", "rt-alert", "grupo-2");
    }

    @Test
    void toqueSemJsVoltaParaOMesDoGrupo() throws Exception {
        mvc.perform(marcar(500L, "PODE").with(user(ANA)))
                .andExpect(redirectedUrl("/disponibilidade?mes=2026-11#grupo-1"))
                .andExpect(flash().attributeCount(0));

        verify(disponibilidades).marcar(30L, 1L, 500L, Resposta.PODE);
    }

    @Test
    void idDeOutraPessoaNoPostEIgnorado() throws Exception {
        mvc.perform(marcar(500L, "PODE")
                        .param("usuarioId", "99")
                        .param("marcadoPorId", "99")
                        .with(user(ANA)))
                .andExpect(status().is3xxRedirection());

        verify(disponibilidades).marcar(30L, 1L, 500L, Resposta.PODE);
        verify(disponibilidades, never()).marcar(eq(99L), anyLong(), anyLong(), any());
    }

    @Test
    void semCsrfNaoGrava() throws Exception {
        mvc.perform(post("/disponibilidade/ministerios/1/eventos/500")
                        .param("resposta", "PODE")
                        .param("mes", "2026-11")
                        .with(user(ANA)))
                .andExpect(status().isForbidden());

        verify(disponibilidades, never()).marcar(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void toqueComOPeriodoJaTravadoMostraOGrupoTravadoEOMotivo() throws Exception {
        String motivo = "Sua resposta para 01/11 · Culto da manhã não mudou: o gerente travou a disponibilidade de"
                + " novembro.";
        doThrow(RegraVioladaException.geral(motivo)).when(disponibilidades).marcar(30L, 1L, 500L, Resposta.NAO_PODE);
        when(consulta.doMembro(30L, NOVEMBRO)).thenReturn(tela(midia(true), louvor(true)));

        String html = mvc.perform(
                        marcar(500L, "NAO_PODE").header("HX-Request", "true").with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("<div class=\"rt-alert\" role=\"alert\">", motivo)
                .contains("class=\"rt-avail rt-avail--locked\"", "Disponibilidade travada", "disabled=\"disabled\"");
    }

    @Test
    void recusaSemJsVaiParaATelaNoGrupoCerto() throws Exception {
        doThrow(RegraVioladaException.geral("Recusada."))
                .when(disponibilidades)
                .marcar(30L, 1L, 500L, Resposta.PREFERE_NAO);

        mvc.perform(marcar(500L, "PREFERE_NAO").with(user(ANA)))
                .andExpect(redirectedUrl("/disponibilidade?mes=2026-11#grupo-1"))
                .andExpect(flash().attribute("recusa", "Recusada."))
                .andExpect(flash().attribute("recusaDoGrupo", 1L));

        assertThat(pagina(get("/disponibilidade")
                        .flashAttr("recusa", "Recusada.")
                        .flashAttr("recusaDoGrupo", 1L)
                        .with(user(ANA))))
                .containsOnlyOnce("Recusada.");
    }

    @Test
    void respostaQueNaoExisteE400EEventoDeOutroMinisterioE404() throws Exception {
        doThrow(new NaoEncontradoException("Evento 900 na Mídia"))
                .when(disponibilidades)
                .marcar(30L, 1L, 900L, Resposta.PODE);

        mvc.perform(marcar(500L, "TALVEZ").with(user(ANA))).andExpect(status().isBadRequest());
        mvc.perform(marcar(900L, "PODE").header("HX-Request", "true").with(user(ANA)))
                .andExpect(status().isNotFound());
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    private static MockHttpServletRequestBuilder marcar(Long eventoId, String resposta) {
        return post("/disponibilidade/ministerios/1/eventos/{e}", eventoId)
                .param("resposta", resposta)
                .param("mes", "2026-11")
                .with(csrf());
    }

    private GrupoDeDisponibilidade midia(boolean travado) {
        return new GrupoDeDisponibilidade(1L, "Mídia", "mint", NOVEMBRO, travado, !travado, List.of(manha, noite));
    }

    private GrupoDeDisponibilidade louvor(boolean travado) {
        return new GrupoDeDisponibilidade(2L, "Louvor", "rose", NOVEMBRO, travado, !travado, List.of(ensaio));
    }

    private static TelaDaDisponibilidade tela(GrupoDeDisponibilidade... grupos) {
        return new TelaDaDisponibilidade(NOVEMBRO, true, List.of(grupos));
    }
}
