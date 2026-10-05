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

    @BeforeEach
    void semMinisterios() {
        when(disponibilidade.doMembro(any())).thenReturn(ResumoDaDisponibilidade.vazio());
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
}
