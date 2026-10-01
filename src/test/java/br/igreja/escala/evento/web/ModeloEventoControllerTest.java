package br.igreja.escala.evento.web;

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
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.service.DadosDoModelo;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.evento.service.ModeloResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(ModeloEventoController.class)
class ModeloEventoControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    MinisterioService ministerios;

    @MockitoBean
    ModeloEventoService modelos;

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
    }

    @Test
    void soOGerenteDoMinisterioVeEMexeNosModelos() throws Exception {
        mvc.perform(get("/ministerios/1/eventos/modelos").with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(criar(1).with(user(MEMBRO))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/2/eventos/modelos").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isForbidden());
        mvc.perform(criar(2).with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/2/eventos/modelos/300")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Culto"))
                .andExpect(status().isForbidden());
        verify(modelos, never()).criar(anyLong(), any());
        verify(modelos, never()).alterar(anyLong(), anyLong(), any());
    }

    @Test
    void listaComDiaHorarioEEstado() throws Exception {
        when(modelos.resumos(1L))
                .thenReturn(List.of(
                        new ModeloResumo(300L, "Culto de domingo", "Domingo", "18h00", true),
                        new ModeloResumo(301L, "Ensaio", "Sábado", "15h00", false)));

        String html = pagina(
                get("/ministerios/1/eventos/modelos").with(user(GERENTE_DA_MIDIA)), ModeloEventoController.LISTA);

        assertThat(html)
                .contains("Mídia — Modelos de evento", "Domingo · 18h00", "Sábado · 15h00 · Inativo")
                .contains("href=\"/ministerios/1/eventos/modelos/300\"", "Criar modelo", "Eventos do mês");
    }

    @Test
    void formularioNovoTemDomingoPrimeiroEHorarioComoTime() throws Exception {
        String html =
                pagina(get("/ministerios/1/eventos/modelos/novo").with(user(GERENTE_DA_MIDIA)), "evento/modelo-form");

        assertThat(html)
                .contains("<option value=\"\">Escolha o dia</option> <option value=\"SUNDAY\">Domingo</option>")
                .contains("type=\"time\"", "type=\"checkbox\" id=\"ativo\" name=\"ativo\" value=\"true\" checked");
    }

    @Test
    void criaEVoltaParaALista() throws Exception {
        when(modelos.criar(1L, new DadosDoModelo("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), true)))
                .thenReturn(ExemplosDeEvento.cultoDeDomingo(1L));

        mvc.perform(criar(1).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/eventos/modelos"))
                .andExpect(flash().attribute("sucesso", "Modelo Culto de domingo criado"));
    }

    @Test
    void formularioIncompletoVoltaComOsErros() throws Exception {
        String html = pagina(
                post("/ministerios/1/eventos/modelos")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "")
                        .param("horario", "")
                        .param("_ativo", "on"),
                "evento/modelo-form");

        assertThat(html)
                .contains("Informe o nome do evento, como Culto de domingo.", "Escolha o dia da semana.")
                .contains("Informe o horário, como 18:00.");
        verify(modelos, never()).criar(anyLong(), any());
    }

    @Test
    void edicaoMostraOHorarioNoFormatoDoCampoEDesativa() throws Exception {
        when(modelos.buscar(1L, 300L)).thenReturn(ExemplosDeEvento.cultoDeDomingo(1L));
        when(modelos.alterar(any(), any(), any())).thenReturn(ExemplosDeEvento.cultoDeDomingo(1L));

        assertThat(pagina(get("/ministerios/1/eventos/modelos/300").with(user(GERENTE_DA_MIDIA)), "evento/modelo-form"))
                .contains("value=\"18:00\"", "<option value=\"SUNDAY\" selected=\"selected\">Domingo</option>");
        mvc.perform(post("/ministerios/1/eventos/modelos/300")
                        .with(user(GERENTE_DA_MIDIA))
                        .with(csrf())
                        .param("nome", "Culto de domingo")
                        .param("diaDaSemana", "SUNDAY")
                        .param("horario", "19:00")
                        .param("_ativo", "on"))
                .andExpect(redirectedUrl("/ministerios/1/eventos/modelos"));
        verify(modelos)
                .alterar(1L, 300L, new DadosDoModelo("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(19, 0), false));
    }

    @Test
    void modeloDeOutroMinisterioE404() throws Exception {
        when(modelos.buscar(1L, 999L)).thenThrow(new NaoEncontradoException("Modelo 999"));

        mvc.perform(get("/ministerios/1/eventos/modelos/999").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/ministerios/1/eventos/modelos")
                        .with(user(GERENTE_DA_MIDIA))
                        .param("nome", "Culto"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder criar(long ministerioId) {
        return post("/ministerios/{m}/eventos/modelos", ministerioId)
                .with(csrf())
                .param("nome", "Culto de domingo")
                .param("diaDaSemana", "SUNDAY")
                .param("horario", "18:00")
                .param("ativo", "true")
                .param("_ativo", "on");
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
