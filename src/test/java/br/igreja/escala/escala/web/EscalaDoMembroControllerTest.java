package br.igreja.escala.escala.web;

import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.web.MinhasEscalas;
import br.igreja.escala.escala.service.CelulaDaGrade;
import br.igreja.escala.escala.service.EscalaDoMembro;
import br.igreja.escala.escala.service.EscalaDoMinisterio;
import br.igreja.escala.escala.service.LinhaDaGrade;
import br.igreja.escala.escala.service.SlotDaGrade;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeController(EscalaDoMembroController.class)
@Import(EscalaDoMembroControllerTest.Relogio.class)
class EscalaDoMembroControllerTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EscalaDoMembro escalas;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock relogio() {
            return Clock.fixed(
                    ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO);
        }
    }

    @BeforeEach
    void prepara() {
        when(escalas.ministerios(any()))
                .thenReturn(List.of(
                        new MinhasEscalas.Ministerio("Louvor", "rose", "/escalas/2"),
                        new MinhasEscalas.Ministerio("Mídia", "mint", "/escalas/1")));
        when(escalas.doMinisterio(1L, OUTUBRO, MEMBRO)).thenReturn(publicada());
        when(escalas.doMinisterio(1L, NOVEMBRO, MEMBRO))
                .thenReturn(new EscalaDoMinisterio(1L, "Mídia", "mint", NOVEMBRO, false, List.of(), List.of()));
        when(escalas.doMinisterio(3L, OUTUBRO, MEMBRO)).thenThrow(new NaoEncontradoException("Ministério 3"));
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/escalas/1")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void escalaPublicadaMostraAGradeSoDeLeituraNoMesDeHoje() throws Exception {
        String html = mvc.perform(get("/escalas/1").with(user(MEMBRO)))
                .andExpect(status().isOk())
                .andExpect(view().name(EscalaDoMembroController.PAGINA))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("<title>Mídia — Outubro · Escala</title>", "<h1 class=\"text-title\">Mídia — Outubro</h1>")
                .contains("rt-badge bg-tint-mint", "<table class=\"rt-table rt-sched\">", "Ana Souza")
                .contains("href=\"/escalas/1?mes=2026-09\"", "href=\"/escalas/1?mes=2026-11\"")
                .contains("href=\"/escalas/2?mes=2026-10\"")
                .doesNotContain("/ministerios/", "hx-get", "rt-btn--primary");
    }

    @Test
    void rascunhoNaoAparecePorUrlDireta() throws Exception {
        String html = mvc.perform(get("/escalas/1").param("mes", "2026-11").with(user(MEMBRO)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("A escala de novembro ainda não foi publicada.")
                .doesNotContain("rt-sched", "Ana Souza");
    }

    @Test
    void ministerioDeQueNaoEMembroE404() throws Exception {
        mvc.perform(get("/escalas/3").with(user(MEMBRO))).andExpect(status().isNotFound());
    }

    private static EscalaDoMinisterio publicada() {
        var linha = new LinhaDaGrade(
                "11",
                "Dom",
                "Culto de domingo",
                "18h00",
                false,
                List.of(new CelulaDaGrade(
                        "Projeção",
                        true,
                        List.of(SlotDaGrade.de(7L, 0, "Ana Souza", "Experiente", false, false, null, null, false)))));
        return new EscalaDoMinisterio(1L, "Mídia", "mint", OUTUBRO, true, List.of("Projeção"), List.of(linha));
    }
}
