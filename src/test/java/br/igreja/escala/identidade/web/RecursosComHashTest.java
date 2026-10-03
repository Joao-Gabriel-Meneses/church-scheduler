package br.igreja.escala.identidade.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O CSS e o JS saem com o hash do conteúdo no nome. O Cloudflare e o navegador guardam esses arquivos por um ano; um
 * deploy que muda o arquivo muda a URL, e ninguém fica com o CSS velho.
 */
@TesteDeController(LoginController.class)
class RecursosComHashTest {

    private static final String HASH = "-[0-9a-f]{32}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    @Test
    void oHtmlApontaParaOsRecursosComHash() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.matchesRegex("(?s).*href=\"/css/app" + HASH + "\\.css\".*")))
                .andExpect(content().string(Matchers.matchesRegex("(?s).*src=\"/js/app" + HASH + "\\.js\".*")))
                .andExpect(content().string(Matchers.matchesRegex("(?s).*src=\"/js/htmx\\.min" + HASH + "\\.js\".*")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("\"/css/app.css\""))));
    }

    @Test
    void aUrlComHashServeOArquivo() throws Exception {
        var html = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        var css = html.replaceAll("(?s).*href=\"(/css/app" + HASH + "\\.css)\".*", "$1");

        mvc.perform(get(css)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/css"));
    }
}
