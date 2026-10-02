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
    void maximoPorNivelSoValeLigadoEComNivel() {
        var ligado = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MaxPorNivelParams(200L, 1))));
        var desligado = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, false, new MaxPorNivelParams(200L, 1))));

        assertThat(ligado.maximoPorNivel()).contains(new MaxPorNivelParams(200L, 1));
        assertThat(desligado.maximoPorNivel()).isEmpty();
    }
}
