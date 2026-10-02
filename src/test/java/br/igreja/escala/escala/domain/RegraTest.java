package br.igreja.escala.escala.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RegraTest {

    @Test
    void regraNovaVemComOPadraoDoCatalogo() {
        var limite = new Regra(1L, TipoDeRegra.LIMITE_POR_PERIODO);

        assertThat(limite.getRigidez()).isEqualTo(Rigidez.HARD);
        assertThat(limite.getPeso()).isEqualTo(1);
        assertThat(limite.isAtiva()).isTrue();
        assertThat(limite.getParametros()).isEqualTo(new LimitePorPeriodoParams(3));
        assertThat(ReflectionTestUtils.getField(limite, "parametros")).isEqualTo("{\"maximo\":3}");
    }

    @Test
    void maximoPorNivelNasceDesligadoESemNivel() {
        var maximo = new Regra(1L, TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);

        assertThat(maximo.isAtiva()).isFalse();
        assertThat(maximo.getParametros()).isEqualTo(new MaxPorNivelParams(null, 1));
        assertThat(ReflectionTestUtils.getField(maximo, "parametros")).isEqualTo("{\"nivelId\":null,\"maximo\":1}");
    }

    @Test
    void alteraParametrosELigaOuDesliga() {
        var maximo = new Regra(1L, TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);

        maximo.alterar(new MaxPorNivelParams(200L, 1), true);

        assertThat(maximo.vigente())
                .isEqualTo(new RegraVigente(
                        TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MaxPorNivelParams(200L, 1)));
    }

    @Test
    void parametrosPrecisamSerDoTipo() {
        var limite = new Regra(1L, TipoDeRegra.LIMITE_POR_PERIODO);

        assertThatIllegalArgumentException().isThrownBy(() -> limite.alterar(new SemParametros(), true));
    }

    @Test
    void regraQueSempreValeNaoSeDesliga() {
        var habilitacao = new Regra(1L, TipoDeRegra.HABILITACAO);

        assertThatIllegalArgumentException().isThrownBy(() -> habilitacao.alterar(new SemParametros(), false));
    }

    @Test
    void jsonTortoNoBancoNaoViraOutraRegra() {
        var limite = new Regra(1L, TipoDeRegra.LIMITE_POR_PERIODO);

        for (String torto : new String[] {"{}", "{\"maximo\":null}", "{\"maximo\":0}", "{\"maximo\":3,\"x\":1}"}) {
            ReflectionTestUtils.setField(limite, "parametros", torto);
            assertThatIllegalArgumentException()
                    .as(torto)
                    .isThrownBy(limite::getParametros)
                    .withMessageContaining("LimitePorPeriodoParams");
        }
    }
}
