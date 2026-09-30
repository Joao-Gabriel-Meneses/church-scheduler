package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class NivelTest {

    private final Ministerio midia = new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR);

    @Test
    void criaComNomeEOrdem() {
        var iniciante = new Nivel(midia, " Iniciante ", 1);

        assertThat(iniciante.getNome()).isEqualTo("Iniciante");
        assertThat(iniciante.getOrdem()).isEqualTo(1);
        assertThat(iniciante.getMinisterio()).isSameAs(midia);
    }

    @Test
    void alteraNomeEOrdem() {
        var nivel = new Nivel(midia, "Iniciante", 1);

        nivel.alterar("Experiente", 2);

        assertThat(nivel.getNome()).isEqualTo("Experiente");
        assertThat(nivel.getOrdem()).isEqualTo(2);
    }

    @Test
    void ordemComecaEmUm() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Nivel(midia, "Iniciante", 0))
                .withMessageContaining("ordem");
        assertThatIllegalArgumentException().isThrownBy(() -> new Nivel(midia, "Iniciante", Nivel.ORDEM_MAXIMA + 1));
    }

    @Test
    void exigeMinisterioENome() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Nivel(null, "Iniciante", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Nivel(midia, " ", 1));
    }
}
