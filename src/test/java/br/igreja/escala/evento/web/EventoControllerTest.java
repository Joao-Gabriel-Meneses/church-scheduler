package br.igreja.escala.evento.web;

import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
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
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.DadosDoEvento;
import br.igreja.escala.evento.service.EventoResumo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(EventoController.class)
class EventoControllerTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    EventoService eventos;

    @MockitoBean
    ModeloEventoService modelos;

    @MockitoBean
    PeriodoService periodos;

    private final Periodo novembro = ExemplosDeEvento.periodo(1L, NOVEMBRO);
    private final Evento cultoDoDia1 = ExemplosDeEvento.comId(
            Evento.doModelo(ExemplosDeEvento.cultoDeDomingo(1L), novembro, LocalDate.of(2026, 11, 1)), 500L);
    private final Evento ensaio = ExemplosDeEvento.comId(
            Evento.avulso(novembro, "Ensaio geral", LocalDate.of(2026, 11, 14), LocalTime.of(15, 0), DUAS_HORAS), 501L);

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(modelos.ativos(1L)).thenReturn(List.of(ExemplosDeEvento.cultoDeDomingo(1L)));
        when(periodos.doMes(any(), any())).thenReturn(Optional.empty());
        when(eventos.buscar(1L, 500L)).thenReturn(cultoDoDia1);
        when(eventos.buscar(1L, 501L)).thenReturn(ensaio);
    }

    @Test
    void membroComumNaoVeNemMexeNosEventos() throws Exception {
        mvc.perform(get("/ministerios/1/eventos").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(gerar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(criar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/eventos/500/cancelar")
                        .with(user(MEMBRO))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verify(eventos, never()).gerarDoMes(anyLong(), any());
        verify(eventos, never()).cancelar(anyLong(), anyLong());
    }

    @Test
    void gerenteDaMidiaNaoMexeNosEventosDoLouvorNemPorPostDireto() throws Exception {
        mvc.perform(get("/ministerios/2/eventos").with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(gerar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(criar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/eventos/500/cancelar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/eventos/500")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Culto")
                        .param("data", "2026-11-01")
                        .param("horario", "19:00")
                        .param("duracaoMinutos", "120"))
                .andExpect(status().isForbidden());
        verify(eventos, never()).gerarDoMes(anyLong(), any());
        verify(eventos, never()).criarAvulso(anyLong(), any());
        verify(eventos, never()).alterar(anyLong(), anyLong(), any());
        verify(eventos, never()).cancelar(anyLong(), anyLong());
    }

    @Test
    void semMesAbreOProximoComOSeletorDePeriodo() throws Exception {
        when(eventos.doMes(1L, NOVEMBRO))
                .thenReturn(List.of(
                        new EventoResumo(
                                500L, "Culto de domingo", "01", "Dom", "01/11 · Dom", "18h00 às 20h00", false, false),
                        new EventoResumo(
                                501L, "Ensaio geral", "14", "Sáb", "14/11 · Sáb", "15h00 às 17h00", true, true)));

        String html = pagina(get("/ministerios/1/eventos").with(user(GERENTE_DA_MIDIA)), EventoController.LISTA);

        assertThat(html)
                .contains("<h1 class=\"text-title\">Mídia — Novembro</h1>", "Novembro 2026", "Nov 2026")
                .contains("href=\"/ministerios/1/eventos?mes=2026-10\"", "href=\"/ministerios/1/eventos?mes=2026-12\"")
                .contains("Disponibilidade aberta")
                .contains("01/11 · Dom · 18h00 às 20h00", "14/11 · Sáb · 15h00 às 17h00 · Avulso · Cancelado")
                .contains("action=\"/ministerios/1/eventos/gerar\"", "name=\"mes\" value=\"2026-11\"")
                .contains("Criar eventos do mês", "Criar evento avulso", "Modelos de evento");
    }

    @Test
    void mesEscolhidoEComTravaMostraOBadgeTravado() throws Exception {
        var outubro = YearMonth.of(2026, 10);
        var travado = ExemplosDeEvento.periodo(1L, outubro);
        ReflectionTestUtils.setField(travado, "disponibilidadeTravada", true);
        when(periodos.doMes(1L, outubro)).thenReturn(Optional.of(travado));
        when(eventos.doMes(1L, outubro)).thenReturn(List.of());

        assertThat(pagina(
                        get("/ministerios/1/eventos").param("mes", "2026-10").with(user(GERENTE_DA_MIDIA)),
                        EventoController.LISTA))
                .contains("Mídia — Outubro", "Disponibilidade travada", "Nenhum evento em outubro ainda.");
    }

    @Test
    void semModelosAtivosAvisaENaoOfereceGerar() throws Exception {
        when(modelos.ativos(1L)).thenReturn(List.of());
        when(eventos.doMes(1L, NOVEMBRO)).thenReturn(List.of());

        assertThat(pagina(get("/ministerios/1/eventos").with(user(GERENTE_DA_MIDIA)), EventoController.LISTA))
                .contains("Nenhum modelo de evento ativo", "href=\"/ministerios/1/eventos/modelos/novo\"")
                .doesNotContain("Criar eventos do mês");
    }

    @Test
    void gerarDizQuantosEventosForamCriados() throws Exception {
        when(eventos.gerarDoMes(1L, NOVEMBRO)).thenReturn(9, 1, 0);

        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/eventos?mes=2026-11"))
                .andExpect(flash().attribute("sucesso", "9 eventos criados em novembro"));
        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "1 evento criado em novembro"));
        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Nenhum evento novo em novembro: os dos modelos já existem"));
    }

    @Test
    void gerarMesQueJaPassouVoltaComOMotivo() throws Exception {
        when(eventos.gerarDoMes(1L, NOVEMBRO))
                .thenThrow(RegraVioladaException.geral("Novembro 2026 já passou: não dá para criar eventos nele."));

        mvc.perform(gerar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/eventos?mes=2026-11"))
                .andExpect(flash().attribute("recusa", "Novembro 2026 já passou: não dá para criar eventos nele."));
    }

    @Test
    void criaAvulsoEVaiParaOMesDele() throws Exception {
        when(eventos.criarAvulso(
                        1L, new DadosDoEvento("Ensaio geral", LocalDate.of(2026, 11, 14), LocalTime.of(15, 0), 120)))
                .thenReturn(ensaio);

        mvc.perform(criar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/eventos?mes=2026-11"))
                .andExpect(flash().attribute("sucesso", "Evento Ensaio geral criado"));
    }

    @Test
    void avulsoInvalidoVoltaComOsErrosNosCampos() throws Exception {
        String html = pagina(
                post("/ministerios/1/eventos")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "")
                        .param("data", "")
                        .param("horario", ""),
                "evento/evento-form");

        assertThat(html).contains("Informe o nome do evento.", "Informe a data.", "Informe o horário, como 19:30.");
        verify(eventos, never()).criarAvulso(anyLong(), any());
    }

    @Test
    void duracaoForaDoIntervaloVoltaComErro() throws Exception {
        String html = pagina(
                post("/ministerios/1/eventos")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Ensaio")
                        .param("data", "2026-11-14")
                        .param("horario", "15:00")
                        .param("duracaoMinutos", "5"),
                "evento/evento-form");

        assertThat(html).contains("id=\"duracaoMinutos-erro\"", "A duração vai de 15 a 1440 minutos.");
        verify(eventos, never()).criarAvulso(anyLong(), any());
    }

    @Test
    void avulsoNoPassadoApareceNoCampoData() throws Exception {
        when(eventos.criarAvulso(anyLong(), any()))
                .thenThrow(new RegraVioladaException("data", "Escolha hoje ou uma data futura."));

        assertThat(pagina(criar(1).with(user(GERENTE_DA_MIDIA)), "evento/evento-form"))
                .contains("id=\"data-erro\"", "Escolha hoje ou uma data futura.");
    }

    @Test
    void eventoDoModeloNaoTemCampoDeDataETemCancelar() throws Exception {
        String html = pagina(get("/ministerios/1/eventos/500").with(user(GERENTE_DA_MIDIA)), "evento/evento-form");

        assertThat(html)
                .contains("Culto de domingo de 01/11", "Criado do modelo Culto de domingo.")
                .contains("type=\"hidden\" id=\"data\" name=\"data\" value=\"2026-11-01\"", "01/11 · Dom")
                .contains("type=\"time\"", "value=\"18:00\"", "aria-describedby=\"duracaoMinutos-dica\" value=\"120\">")
                .contains("popovertarget=\"cancelar-evento\"", "action=\"/ministerios/1/eventos/500/cancelar\"")
                .contains("Manter evento")
                .doesNotContain("type=\"date\"");
    }

    @Test
    void avulsoTemCampoDeDataEOCanceladoOfereceReativar() throws Exception {
        ensaio.cancelar();

        assertThat(pagina(get("/ministerios/1/eventos/501").with(user(GERENTE_DA_MIDIA)), "evento/evento-form"))
                .contains("type=\"date\"", "value=\"2026-11-14\"", "Avulso", "Cancelado")
                .contains("action=\"/ministerios/1/eventos/501/reativar\"", "Reativar evento")
                .doesNotContain("cancelar-evento");
    }

    @Test
    void salvaCancelaEReativa() throws Exception {
        when(eventos.alterar(
                        1L,
                        500L,
                        new DadosDoEvento("Culto de domingo", LocalDate.of(2026, 11, 1), LocalTime.of(19, 0), 120)))
                .thenReturn(cultoDoDia1);
        when(eventos.cancelar(1L, 500L)).thenReturn(cultoDoDia1);
        when(eventos.reativar(1L, 500L)).thenReturn(cultoDoDia1);

        mvc.perform(post("/ministerios/1/eventos/500")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Culto de domingo")
                        .param("data", "2026-11-01")
                        .param("horario", "19:00")
                        .param("duracaoMinutos", "120"))
                .andExpect(redirectedUrl("/ministerios/1/eventos?mes=2026-11"))
                .andExpect(flash().attribute("sucesso", "Evento Culto de domingo de 01/11 salvo"));
        mvc.perform(post("/ministerios/1/eventos/500/cancelar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "Culto de domingo de 01/11 cancelado"));
        mvc.perform(post("/ministerios/1/eventos/500/reativar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(flash().attribute("sucesso", "Culto de domingo de 01/11 reativado"));
    }

    @Test
    void mudarADataDeEventoDoModeloApareceNoCampoData() throws Exception {
        when(eventos.alterar(anyLong(), anyLong(), any()))
                .thenThrow(new RegraVioladaException("data", "A data de um evento do modelo não muda."));

        assertThat(pagina(
                        post("/ministerios/1/eventos/500")
                                .with(user(GERENTE_DA_MIDIA))
                                .with(csrf())
                                .param("nome", "Culto de domingo")
                                .param("data", "2026-11-02")
                                .param("horario", "19:00")
                                .param("duracaoMinutos", "120"),
                        "evento/evento-form"))
                .contains("A data de um evento do modelo não muda.");
    }

    @Test
    void eventoDeOutroMinisterioE404() throws Exception {
        when(eventos.buscar(1L, 999L)).thenThrow(new NaoEncontradoException("Evento 999"));
        when(eventos.cancelar(1L, 999L)).thenThrow(new NaoEncontradoException("Evento 999"));

        mvc.perform(get("/ministerios/1/eventos/999").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/1/eventos/999/cancelar")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/eventos/gerar")
                        .with(user(GERENTE_DA_MIDIA))
                        .param("mes", "2026-11"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/1/eventos/500/cancelar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder gerar(long ministerioId) {
        return post("/ministerios/{m}/eventos/gerar", ministerioId).with(csrf()).param("mes", "2026-11");
    }

    private static MockHttpServletRequestBuilder criar(long ministerioId) {
        return post("/ministerios/{m}/eventos", ministerioId)
                .with(csrf())
                .param("nome", "Ensaio geral")
                .param("data", "2026-11-14")
                .param("horario", "15:00")
                .param("duracaoMinutos", "120");
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
