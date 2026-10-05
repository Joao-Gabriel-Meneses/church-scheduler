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
    void ajustarTrocaAPessoaEFixaInclusiveVazia() {
        var vaga = new Vaga(500L, 100L, 1);
        vaga.forcar(30L, "Só ela sabe operar a mesa nova");

        vaga.ajustar(31L);
        assertThat(vaga.getUsuarioId()).isEqualTo(31L);
        assertThat(vaga.isFixada()).isTrue();
        assertThat(vaga.isForcada()).isFalse();
        assertThat(vaga.getJustificativa()).isNull();

        vaga.ajustar(null);
        assertThat(vaga.isVazia()).isTrue();
        assertThat(vaga.isPresa())
                .as("esvaziada à mão fica vazia ao gerar de novo")
                .isTrue();
    }

    @Test
    void forcarExigePessoaEJustificativaEFixa() {
        var vaga = new Vaga(500L, 100L, 1);

        vaga.forcar(30L, "  Única que pode nesse dia  ");

        assertThat(vaga.isForcada()).isTrue();
        assertThat(vaga.isFixada()).isTrue();
        assertThat(vaga.getJustificativa()).isEqualTo("Única que pode nesse dia");
        assertThatIllegalArgumentException().isThrownBy(() -> vaga.forcar(30L, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> vaga.forcar(null, "Motivo"));
        assertThatIllegalArgumentException().isThrownBy(() -> vaga.forcar(30L, "x".repeat(501)));
    }

    @Test
    void fixarMantemAPessoaEDesafixarSoltaATeAForcada() {
        var vaga = new Vaga(500L, 100L, 1);
        vaga.escalar(30L);

        vaga.fixar();
        assertThat(vaga.isFixada()).isTrue();
        assertThat(vaga.getUsuarioId()).isEqualTo(30L);

        vaga.forcar(31L, "Motivo");
        vaga.fixar();
        assertThat(vaga.isForcada()).as("fixar não tira a forçada").isTrue();

        vaga.desafixar();
        assertThat(vaga.isPresa()).isFalse();
        assertThat(vaga.getJustificativa()).isNull();
        assertThat(vaga.getUsuarioId()).as("a pessoa fica até gerar de novo").isEqualTo(31L);
    }

    @Test
    void posicaoComecaEmUm() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Vaga(500L, 100L, 0));
    }
}
