package br.igreja.escala.escala.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MinPorNivelParamsTest {

    @Test
    void oMinimoVaiDeUmAVinte() {
        assertThat(new MinPorNivelParams(200L, 1).minimo()).isEqualTo(1);
        assertThat(new MinPorNivelParams(200L, 20).minimo()).isEqualTo(20);
        assertThatThrownBy(() -> new MinPorNivelParams(200L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MinPorNivelParams(200L, 21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oJsonDaRegraAntigaNaoViraORegraNova() {
        assertThat(ParametrosEmJson.ler("{\"nivelId\":201,\"minimo\":1}", MinPorNivelParams.class))
                .isEqualTo(new MinPorNivelParams(201L, 1));
        assertThatThrownBy(() -> ParametrosEmJson.ler("{\"nivelId\":200,\"maximo\":1}", MinPorNivelParams.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParametrosEmJson.ler("{\"nivelId\":200}", MinPorNivelParams.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
