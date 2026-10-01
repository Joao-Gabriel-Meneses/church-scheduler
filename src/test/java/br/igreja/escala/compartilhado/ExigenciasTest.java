package br.igreja.escala.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ExigenciasTest {

    @Test
    void textoTiraOsEspacosDasPontas() {
        assertThat(Exigencias.texto("  Mídia ", "nome", 10)).isEqualTo("Mídia");
    }

    @Test
    void textoRecusaVazioNuloELongoDemais() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Exigencias.texto(" ", "nome", 10))
                .withMessage("nome é obrigatório");
        assertThatIllegalArgumentException().isThrownBy(() -> Exigencias.texto(null, "nome", 10));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Exigencias.texto("Transmissão", "nome", 5))
                .withMessage("nome tem mais de 5 caracteres");
    }

    @Test
    void presenteDevolveOValorOuRecusaNulo() {
        assertThat(Exigencias.presente("x", "campo")).isEqualTo("x");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Exigencias.presente(null, "cor"))
                .withMessage("cor é obrigatório");
    }

    @Test
    void entreAceitaOsLimitesERecusaForaDeles() {
        assertThat(Exigencias.entre(1, 1, 3, "qtd")).isEqualTo(1);
        assertThat(Exigencias.entre(3, 1, 3, "qtd")).isEqualTo(3);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Exigencias.entre(4, 1, 3, "qtd"))
                .withMessage("qtd precisa estar entre 1 e 3");
        assertThatIllegalArgumentException().isThrownBy(() -> Exigencias.entre(0, 1, 3, "qtd"));
    }
}
