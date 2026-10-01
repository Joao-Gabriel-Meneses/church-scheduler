package br.igreja.escala.compartilhado.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.compartilhado.Fuso;
import org.junit.jupiter.api.Test;

class RelogioConfigTest {

    /** "Hoje" e "este mês" saem deste relógio; com o fuso da JVM (UTC no Docker), às 21h já seria amanhã. */
    @Test
    void relogioDaAplicacaoEstaEmSaoPaulo() {
        assertThat(new RelogioConfig().relogio().getZone()).isEqualTo(Fuso.SAO_PAULO);
    }
}
