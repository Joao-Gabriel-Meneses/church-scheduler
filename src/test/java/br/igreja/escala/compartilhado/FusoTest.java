package br.igreja.escala.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FusoTest {

    @Test
    void usaOFusoDeSaoPaulo() {
        assertThat(Fuso.SAO_PAULO.getId()).isEqualTo("America/Sao_Paulo");
    }
}
