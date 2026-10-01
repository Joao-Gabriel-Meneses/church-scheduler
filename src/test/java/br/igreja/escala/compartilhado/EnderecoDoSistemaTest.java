package br.igreja.escala.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class EnderecoDoSistemaTest {

    private final Validator validador =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void linkJuntaOEnderecoEOCaminhoSemBarraDupla() {
        var endereco = new EnderecoDoSistema(" https://escala.exemplo.com.br/ ");

        assertThat(endereco.urlBase()).isEqualTo("https://escala.exemplo.com.br");
        assertThat(endereco.link("/disponibilidade?mes=2026-11"))
                .isEqualTo("https://escala.exemplo.com.br/disponibilidade?mes=2026-11");
        assertThat(endereco.link("conta")).isEqualTo("https://escala.exemplo.com.br/conta");
    }

    @Test
    void precisaSerUmEnderecoHttpComDominio() {
        assertThat(validador.validate(new EnderecoDoSistema("http://localhost:8080")))
                .isEmpty();
        assertThat(validador.validate(new EnderecoDoSistema("https://")))
                .as("DOMINIO vazio")
                .isNotEmpty();
        assertThat(validador.validate(new EnderecoDoSistema("escala.exemplo.com.br")))
                .isNotEmpty();
        assertThat(validador.validate(new EnderecoDoSistema(" "))).isNotEmpty();
        assertThat(validador.validate(new EnderecoDoSistema(null))).isNotEmpty();
    }
}
