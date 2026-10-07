package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TesteDeController(InicioController.class)
class InicioControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @MockitoBean
    DisponibilidadeNoInicio disponibilidade;

    @MockitoBean
    EscalasNoInicio escalas;

    @BeforeEach
    void semMinisterios() {
        when(disponibilidade.doMembro(any())).thenReturn(ResumoDaDisponibilidade.vazio());
        when(escalas.doMembro(any())).thenReturn(MinhasEscalas.vazio());
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void cumprimentaOMembroPeloNomeSemSeloDeAdmin() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Olá, <span>Ana</span>!")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Administrador"))));
    }

    @Test
    void mostraOSeloDeAdministrador() throws Exception {
        var bia = new UsuarioAutenticado(Usuario.admin("Bia", "bia@x.com", "hash"));

        mvc.perform(get("/").with(user(bia)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Administrador")));
    }

    @Test
    void juntaMinhasEscalasEADisponibilidadeDoProximoMesNaMesmaTela() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(disponibilidade.doMembro(any()))
                .thenReturn(new ResumoDaDisponibilidade(
                        "Novembro",
                        "/disponibilidade?mes=2026-11",
                        List.of(
                                new ResumoDaDisponibilidade.Ministerio("Mídia", "4 de 5 respondidos", false),
                                new ResumoDaDisponibilidade.Ministerio("Louvor", "2 de 2 respondidos", true))));

        var html = mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("Minhas escalas", "Disponibilidade — Novembro")
                .contains("Mídia", "4 de 5 respondidos", "Travada · 2 de 2 respondidos")
                .contains("href=\"/disponibilidade?mes=2026-11\"");
    }

    @Test
    void minhasEscalasMostraAsProximasComAEtiquetaDoMinisterioEAsPassadasRecolhidas() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(escalas.doMembro(any()))
                .thenReturn(new MinhasEscalas(
                        List.of(new MinhasEscalas.Escala(
                                "12/10 · Dom · 18h00",
                                "Projeção",
                                "Culto de domingo",
                                "Mídia",
                                "mint",
                                "/escalas/1?mes=2026-10",
                                7L,
                                true)),
                        List.of(new MinhasEscalas.Escala(
                                "04/10 · Dom · 18h00",
                                "Transmissão",
                                "Culto de domingo",
                                "Louvor",
                                "rose",
                                "/escalas/2?mes=2026-10",
                                3L,
                                false)),
                        List.of(new MinhasEscalas.Ministerio("Mídia", "mint", "/escalas/1"))));

        var html = mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("12/10 · Dom · 18h00", "Projeção · Culto de domingo")
                .contains("rt-badge bg-tint-mint flex-none", "rt-badge bg-tint-rose flex-none")
                .contains("<details", "Escalas passadas (1)", "04/10 · Dom · 18h00", "href=\"/escalas/2?mes=2026-10\"")
                .contains("Escala do ministério:", "href=\"/escalas/1\"")
                .doesNotContain("Suas próximas escalas vão aparecer aqui");
    }

    @Test
    void cadaProximaEscalaTemDesistirComConfirmacaoNumSheet() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(escalas.doMembro(any()))
                .thenReturn(new MinhasEscalas(
                        List.of(new MinhasEscalas.Escala(
                                "12/10 · Dom · 18h00",
                                "Projeção",
                                "Culto de domingo",
                                "Mídia",
                                "mint",
                                "/escalas/1?mes=2026-10",
                                7L,
                                true)),
                        List.of(),
                        List.of()));

        var html = mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("popovertarget=\"desistir-7\"", "Desistir </button>")
                .contains("id=\"desistir-7\" popover", "Desistir de Projeção, 12/10 · Dom · 18h00?")
                .contains("Culto de domingo · Mídia. A vaga fica vazia na hora")
                .contains("action=\"/escalas/vagas/7/desistir\"", "name=\"_csrf\"")
                .contains("Manter escala", "Confirmar desistência")
                .doesNotContain("Para sair desta escala agora");
    }

    @Test
    void comMenosDe24HorasNaoTemDesistirEDizQueEComOGerente() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(escalas.doMembro(any()))
                .thenReturn(new MinhasEscalas(
                        List.of(new MinhasEscalas.Escala(
                                "08/10 · Qui · 19h30",
                                "Projeção",
                                "Culto de quinta",
                                "Mídia",
                                "mint",
                                "/escalas/1?mes=2026-10",
                                8L,
                                false)),
                        List.of(),
                        List.of()));

        var html = mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("08/10 · Qui · 19h30", "Para sair desta escala agora, fale com o gerente.")
                .doesNotContain("desistir-8", "/escalas/vagas/8/desistir", "calendar-x");
    }

    @Test
    void desistenciaRecusadaVoltaComoAlertaNoTopo() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(get("/").with(user(ana)).flashAttr("recusa", "Faltam menos de 24 h para Culto de quinta."))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("rt-alert")))
                .andExpect(content().string(Matchers.containsString("Faltam menos de 24 h para Culto de quinta.")));
    }

    @Test
    void semEscalaPublicadaAvisaQueVaiAparecerDepois() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));

        mvc.perform(get("/").with(user(ana)))
                .andExpect(content()
                        .string(Matchers.containsString(
                                "Suas próximas escalas vão aparecer aqui depois que o gerente publicar.")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Escalas passadas"))));
    }
}
