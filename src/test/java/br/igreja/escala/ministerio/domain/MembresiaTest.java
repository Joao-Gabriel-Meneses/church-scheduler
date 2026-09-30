package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MembresiaTest {

    private final Ministerio midia = new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR);

    @Test
    void nasceSemSerGerente() {
        var membresia = new Membresia(7L, midia);

        assertThat(membresia.getUsuarioId()).isEqualTo(7L);
        assertThat(membresia.getMinisterio()).isSameAs(midia);
        assertThat(membresia.isGerente()).isFalse();
    }

    @Test
    void tornaERemoveGerente() {
        var membresia = new Membresia(7L, midia);

        membresia.tornarGerente();
        assertThat(membresia.isGerente()).isTrue();

        membresia.removerGerente();
        assertThat(membresia.isGerente()).isFalse();
    }

    @Test
    void exigeUsuarioEMinisterio() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Membresia(null, midia));
        assertThatIllegalArgumentException().isThrownBy(() -> new Membresia(7L, null));
    }
}
