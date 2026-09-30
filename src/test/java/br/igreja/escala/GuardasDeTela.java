package br.igreja.escala;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/** Regras do design conferidas em toda resposta do MockMvc, sem cada teste precisar lembrar delas. */
@TestConfiguration(proxyBeanMethods = false)
public class GuardasDeTela {

    @Bean
    MockMvcBuilderCustomizer umPrimarioPorTela() {
        return mockMvc -> mockMvc.alwaysExpect(new UmPrimarioPorTela());
    }
}
