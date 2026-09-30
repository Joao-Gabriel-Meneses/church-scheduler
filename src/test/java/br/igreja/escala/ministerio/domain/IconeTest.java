package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.visual.IconesTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IconeTest {

    @Test
    void todoIconeEscolhivelEstaNoSprite() {
        assertThat(Arrays.stream(Icone.values()).map(Icone::lucide))
                .as("acrescente o nome em src/main/frontend/icones.json")
                .allMatch(IconesTest.disponiveis()::contains);
    }

    @Test
    void corDaEtiquetaViraOTintDoBadge() {
        assertThat(CorDoMinisterio.MINT.tint()).isEqualTo("mint");
        assertThat(CorDoMinisterio.LEMON.rotulo()).isEqualTo("Amarelo");
    }
}
