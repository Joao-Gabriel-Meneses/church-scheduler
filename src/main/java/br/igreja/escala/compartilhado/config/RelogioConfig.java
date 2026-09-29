package br.igreja.escala.compartilhado.config;

import br.igreja.escala.compartilhado.Fuso;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Relógio injetável no fuso de São Paulo, para que regras de data sejam testáveis. */
@Configuration(proxyBeanMethods = false)
class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.system(Fuso.SAO_PAULO);
    }
}
