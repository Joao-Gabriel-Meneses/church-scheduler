package br.igreja.escala.escala.web;

import static br.igreja.escala.AcessoDeTeste.ADMIN;
import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.TesteDeRotaDoGerente;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.service.Andamento;
import br.igreja.escala.escala.service.ConsultaDaEscala;
import br.igreja.escala.escala.service.GeracaoDaEscala;
import br.igreja.escala.escala.service.PaginaDaEscala;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(EscalaController.class)
@Import(EscalaControllerTest.Relogio.class)
class EscalaControllerTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final Instant AGORA =
            ZonedDateTime.of(2026, 10, 7, 10, 0, 12, 0, Fuso.SAO_PAULO).toInstant();

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    ConsultaDaEscala consulta;

    @MockitoBean
    GeracaoDaEscala geracao;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    EventoService eventos;

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock relogio() {
            return Clock.fixed(AGORA, Fuso.SAO_PAULO);
        }
    }

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(consulta.doMes(1L, NOVEMBRO)).thenReturn(rascunho(true));
        when(geracao.andamento(anyLong(), any())).thenReturn(Optional.empty());
        when(geracao.retirarTerminada(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    void membroComumNaoVeNemGeraAEscala() throws Exception {
        mvc.perform(get("/ministerios/1/escalas").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(gerar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(andamento(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        verify(geracao, never()).iniciar(anyLong(), any(), any());
        verify(consulta, never()).doMes(anyLong(), any());
    }

    @Test
    void gerenteDaMidiaNaoVeNemGeraAEscalaDoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/escalas").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(gerar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(andamento(2).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        verify(geracao, never()).iniciar(anyLong(), any(), any());
        verify(consulta, never()).doMes(anyLong(), any());
    }

    @Test
    void ministerioQueNaoExisteE404() throws Exception {
        mvc.perform(get("/ministerios/9/escalas").with(user(ADMIN))).andExpect(status().isNotFound());
        mvc.perform(gerar(9).with(user(ADMIN))).andExpect(status().isNotFound());
        verify(geracao, never()).iniciar(anyLong(), any(), any());
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/escalas/gerar").param("mes", "2026-11").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        verify(geracao, never()).iniciar(anyLong(), any(), any());
    }

    @Test
    void paginaMostraOGerarEscalaComoPrimarioAGradeEOsAlertas() throws Exception {
        String html = pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA)));

        assertThat(html)
                .contains("<title>Mídia — Novembro · Escala</title>", "<h1 class=\"text-title\">Mídia — Novembro</h1>")
                .contains("class=\"rt-badge rt-badge--draft\"", "Rascunho")
                .contains("<button type=\"submit\" class=\"rt-btn rt-btn--primary\" form=\"gerar-escala\">")
                .contains("Gerar escala", "Disponibilidade travada")
                .contains("<form id=\"gerar-escala\" action=\"/ministerios/1/escalas/gerar\" method=\"post\" hidden>")
                .contains("name=\"mes\" value=\"2026-11\"")
                .contains("href=\"/ministerios/1/regras\"")
                .contains("href=\"/ministerios/1/escalas?mes=2026-10\"", "href=\"/ministerios/1/escalas?mes=2026-12\"")
                .contains("Transmissão, 01/11 · Dom · 18h00 · Culto de domingo")
                .contains("Ninguém habilitado em Transmissão marcou Pode.", "Regra: DISPONIBILIDADE")
                .contains("<div class=\"rt-stat__value\">1</div>", "de 2 · 1 vazia")
                .contains("<table class=\"rt-table rt-sched\">", "<tr class=\"is-alert\">", "Ana Souza")
                .contains("Escalas por pessoa", "1 escala")
                .doesNotContain("Disponibilidade de novembro aberta", "id=\"andamento\"");
    }

    @Test
    void cadaVagaAjustavelAbreNoSheetPorHtmxOuNaPaginaDaVaga() throws Exception {
        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("<a class=\"rt-slot\" href=\"/ministerios/1/escalas/vagas/7?mes=2026-11\"")
                .contains("hx-get=\"/ministerios/1/escalas/vagas/8?mes=2026-11\" hx-target=\"#vaga-conteudo\"")
                .contains("aria-label=\"Vaga vazia. Ajustar a vaga\"")
                .contains("<div id=\"vaga-sheet\" popover", "id=\"vaga-conteudo\"");
    }

    @Test
    void semAjusteAsVagasNaoSaoLinks() throws Exception {
        when(consulta.doMes(1L, NOVEMBRO)).thenReturn(PaginasDeExemplo.rascunho(NOVEMBRO, true, false));

        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .doesNotContain("/escalas/vagas/")
                .contains("<span class=\"rt-slot rt-slot--empty\">");
    }

    @Test
    void comADisponibilidadeAbertaAvisaELevaAoPainel() throws Exception {
        when(consulta.doMes(1L, NOVEMBRO)).thenReturn(rascunho(false));

        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("Disponibilidade de novembro aberta")
                .contains("Trave a disponibilidade para gerar a escala: a geração usa as respostas travadas.")
                .contains("href=\"/ministerios/1/disponibilidade?mes=2026-11\"");
    }

    @Test
    void mesSemEventosNaoOfereceGerar() throws Exception {
        when(consulta.doMes(1L, NOVEMBRO)).thenReturn(semPeriodo());

        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("Novembro ainda não tem eventos.", "href=\"/ministerios/1/eventos?mes=2026-11\"")
                .doesNotContain("Gerar escala", "gerar-escala", "rt-btn--primary");
    }

    @Test
    void gerarComecaAGeracaoEVoltaParaAPagina() throws Exception {
        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas?mes=2026-11"))
                .andExpect(flash().attributeCount(0));

        verify(geracao).iniciar(1L, NOVEMBRO, GERENTE_DA_MIDIA);
    }

    @Test
    void gerarSemTravaVoltaComOMotivo() throws Exception {
        when(geracao.iniciar(1L, NOVEMBRO, GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("Trave a disponibilidade de novembro antes de gerar a escala."));

        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas?mes=2026-11"))
                .andExpect(flash().attribute("recusa", "Trave a disponibilidade de novembro antes de gerar a escala."));
    }

    @Test
    void segundoCliqueAvisaQueJaEstaGerando() throws Exception {
        var gerando = gerando();
        when(geracao.andamento(1L, NOVEMBRO)).thenReturn(Optional.of(gerando));
        when(geracao.iniciar(1L, NOVEMBRO, GERENTE_DA_MIDIA)).thenReturn(gerando);

        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "A escala de novembro já está sendo gerada"));
    }

    @Test
    void duranteAGeracaoAPaginaMostraOAndamentoESemPrimario() throws Exception {
        var gerando = gerando();
        when(geracao.andamento(1L, NOVEMBRO)).thenReturn(Optional.of(gerando));

        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("<section id=\"andamento\"", "role=\"status\"", "hx-trigger=\"every 2s\"")
                .contains("hx-get=\"/ministerios/1/escalas/andamento?mes=2026-11\"")
                .contains("Gerando a escala de novembro", "12 s de até 30 s · 20 de 28 vagas preenchidas até agora")
                .contains("Gerando escala")
                .doesNotContain("rt-btn--primary");
    }

    @Test
    void andamentoComHtmxDevolveSoOPainel() throws Exception {
        var gerando = gerando();
        when(geracao.andamento(1L, NOVEMBRO)).thenReturn(Optional.of(gerando));

        String html = mvc.perform(andamento(1).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(EscalaController.ANDAMENTO))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html.strip()).startsWith("<section id=\"andamento\"").doesNotContain("<html");
    }

    @Test
    void quandoTerminaOHtmxRecarregaAPaginaESemHtmxRedireciona() throws Exception {
        mvc.perform(andamento(1).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/ministerios/1/escalas?mes=2026-11"));
        mvc.perform(andamento(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas?mes=2026-11"));
    }

    @Test
    void geracaoQueTerminouViraToastOuAlerta() throws Exception {
        var concluida = mock(Andamento.class);
        when(concluida.getEstado()).thenReturn(Andamento.Estado.CONCLUIDA);
        when(concluida.getMensagem()).thenReturn("Escala de novembro gerada: 27 de 28 vagas preenchidas");
        var falhou = mock(Andamento.class);
        when(falhou.getEstado()).thenReturn(Andamento.Estado.FALHOU);
        when(falhou.getMensagem()).thenReturn("A disponibilidade de novembro foi destravada durante a geração.");
        when(geracao.retirarTerminada(1L, NOVEMBRO)).thenReturn(Optional.of(concluida), Optional.of(falhou));

        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("class=\"rt-toast\"", "Escala de novembro gerada: 27 de 28 vagas preenchidas");
        assertThat(pagina(get("/ministerios/1/escalas").with(user(GERENTE_DA_MIDIA))))
                .contains("class=\"rt-alert\"", "A disponibilidade de novembro foi destravada durante a geração.");
    }

    private static Andamento gerando() {
        var andamento = mock(Andamento.class);
        when(andamento.isGerando()).thenReturn(true);
        when(andamento.getInicio()).thenReturn(AGORA.minusSeconds(12));
        when(andamento.getPreenchidas()).thenReturn(20);
        when(andamento.getVagas()).thenReturn(28);
        return andamento;
    }

    private static PaginaDaEscala rascunho(boolean travada) {
        return PaginasDeExemplo.rascunho(NOVEMBRO, travada, true);
    }

    private static PaginaDaEscala semPeriodo() {
        return PaginasDeExemplo.semPeriodo(NOVEMBRO);
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andExpect(view().name(EscalaController.PAGINA))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    private static MockHttpServletRequestBuilder gerar(long ministerioId) {
        return post("/ministerios/{m}/escalas/gerar", ministerioId)
                .param("mes", "2026-11")
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder andamento(long ministerioId) {
        return get("/ministerios/{m}/escalas/andamento", ministerioId).param("mes", "2026-11");
    }
}
