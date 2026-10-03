package br.igreja.escala.escala.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VagaTest {

    @Test
    void vagaNovaNasceVaziaESoltaEAGeracaoEscalaEEsvazia() {
        var vaga = new Vaga(500L, 100L, 1);

        assertThat(vaga.isVazia()).isTrue();
        assertThat(vaga.isPresa()).isFalse();

        vaga.escalar(30L);
        assertThat(vaga.getUsuarioId()).isEqualTo(30L);

        vaga.escalar(null);
        assertThat(vaga.isVazia()).isTrue();
    }

    @Test
    void vagaFixadaOuForcadaNaoMudaNaGeracao() {
        var fixada = new Vaga(500L, 100L, 1);
        ReflectionTestUtils.setField(fixada, "fixada", true);
        var forcada = new Vaga(500L, 101L, 1);
        ReflectionTestUtils.setField(forcada, "forcada", true);

        assertThat(fixada.isPresa()).isTrue();
        assertThat(forcada.isPresa()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> fixada.escalar(30L));
        assertThatIllegalStateException().isThrownBy(() -> forcada.escalar(null));
    }

    @Test
    void posicaoComecaEmUm() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Vaga(500L, 100L, 0));
    }
}
