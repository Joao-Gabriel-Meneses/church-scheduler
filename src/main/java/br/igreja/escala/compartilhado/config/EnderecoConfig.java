package br.igreja.escala.compartilhado.config;

import br.igreja.escala.compartilhado.EnderecoDoSistema;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga {@code escala.url-base} ao {@link EnderecoDoSistema}. Sem ela (ou com valor inválido), o app não sobe. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnderecoDoSistema.class)
class EnderecoConfig {}
