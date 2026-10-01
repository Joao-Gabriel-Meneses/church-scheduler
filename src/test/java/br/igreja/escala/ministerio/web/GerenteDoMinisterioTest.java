package br.igreja.escala.ministerio.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.Pessoas;
import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.AcessoAoMinisterio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/** A anotação, com o {@link AcessoAoMinisterio} de verdade, numa rota de exemplo. */
@TesteDeController(GerenteDoMinisterioTest.Rotas.class)
@Import({GerenteDoMinisterioTest.Rotas.class, AcessoAoMinisterio.class})
class GerenteDoMinisterioTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @MockitoBean
    MembresiaRepository membresias;

    @BeforeEach
    void gerenteDaMidia() {
        when(membresias.existsByUsuarioIdAndMinisterioIdAndGerenteTrue(10L, 1L)).thenReturn(true);
    }

    @Test
    void gerenteAbreAsRotasDoProprioMinisterio() throws Exception {
        mvc.perform(get("/ministerios/1/exemplo").with(user(Pessoas.membro(10L, "Gerente"))))
                .andExpect(status().isOk());
    }

    @Test
    void gerenteDeUmMinisterioNaoAbreOutro() throws Exception {
        mvc.perform(get("/ministerios/2/exemplo").with(user(Pessoas.membro(10L, "Gerente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void membroQueNaoEGerenteNaoAbre() throws Exception {
        mvc.perform(get("/ministerios/1/exemplo").with(user(Pessoas.membro(20L, "Membro"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAbreQualquerMinisterio() throws Exception {
        mvc.perform(get("/ministerios/2/exemplo").with(user(Pessoas.admin(1L, "Admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void rotaSemOParametroMinisterioIdNegaAteParaOGerente() throws Exception {
        mvc.perform(get("/ministerios/1/sem-parametro").with(user(Pessoas.membro(10L, "Gerente"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semLoginVaiParaOLogin() throws Exception {
        mvc.perform(get("/ministerios/1/exemplo")).andExpect(redirectedUrl("/login"));
    }

    @RestController
    @RequestMapping("/ministerios/{ministerioId}")
    @GerenteDoMinisterio
    static class Rotas {

        @GetMapping("/exemplo")
        @ResponseBody
        String exemplo(@PathVariable Long ministerioId) {
            return "ok " + ministerioId;
        }

        @GetMapping("/sem-parametro")
        @ResponseBody
        String semParametro() {
            return "não deveria abrir";
        }
    }
}
