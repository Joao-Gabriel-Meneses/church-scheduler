package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContaEditadaTest {

    private static final UsuarioResumo ANA = new UsuarioResumo(1L, "Ana", "ana@x.com", null, false, false, true);

    @Test
    void resumeOsCamposNoSingularENoPlural() {
        assertThat(editada("e-mail").resumo()).isEqualTo("e-mail alterado");
        assertThat(editada("nome", "telefone").resumo()).isEqualTo("nome e telefone alterados");
        assertThat(editada("nome", "e-mail", "telefone").resumo()).isEqualTo("nome, e-mail e telefone alterados");
    }

    @Test
    void semMudancaNaoTemResumo() {
        var nada = editada();

        assertThat(nada.mudou()).isFalse();
        assertThatIllegalStateException().isThrownBy(nada::resumo);
    }

    private static ContaEditada editada(String... campos) {
        return new ContaEditada(ANA, List.of(campos));
    }
}
