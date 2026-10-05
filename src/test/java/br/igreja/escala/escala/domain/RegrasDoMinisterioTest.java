package br.igreja.escala.escala.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegrasDoMinisterioTest {

    @Test
    void tipoQueFaltaValeOPadrao() {
        var regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.LIMITE_POR_PERIODO, Rigidez.HARD, 1, true, new LimitePorPeriodoParams(4))));

        assertThat(regras.limitePorMes()).isEqualTo(4);
        assertThat(regras.todas()).hasSize(TipoDeRegra.values().length);
        assertThat(regras.de(TipoDeRegra.HABILITACAO)).isEqualTo(TipoDeRegra.HABILITACAO.padrao());
    }

    @Test
    void minimoPorNivelSoValeLigadoEComNivel() {
        var ligado = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(200L, 1))));
        var desligado = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, false, new MinPorNivelParams(200L, 1))));

        assertThat(ligado.minimoPorNivel()).contains(new MinPorNivelParams(200L, 1));
        assertThat(desligado.minimoPorNivel()).isEmpty();
    }
}
