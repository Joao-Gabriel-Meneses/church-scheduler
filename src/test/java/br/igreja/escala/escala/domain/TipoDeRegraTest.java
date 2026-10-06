package br.igreja.escala.escala.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TipoDeRegraTest {

    @Test
    void catalogoPadraoTemAsRigidasLigadasOLimiteEmTresEOMinimoPorNivelDesligado() {
        var padrao = RegrasDoMinisterio.padrao();

        assertThat(Arrays.stream(TipoDeRegra.values()).filter(tipo -> tipo.rigidezPadrao() == Rigidez.HARD))
                .containsExactly(
                        TipoDeRegra.PESSOAS_POR_FUNCAO,
                        TipoDeRegra.HABILITACAO,
                        TipoDeRegra.DISPONIBILIDADE,
                        TipoDeRegra.UMA_FUNCAO_POR_EVENTO,
                        TipoDeRegra.SEM_SOBREPOSICAO,
                        TipoDeRegra.LIMITE_POR_PERIODO,
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
        assertThat(padrao.todas())
                .filteredOn(regra -> regra.tipo() != TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO)
                .allMatch(RegraVigente::ativa);
        assertThat(padrao.limitePorMes()).isEqualTo(3);
        assertThat(padrao.ativa(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO)).isFalse();
        assertThat(padrao.minimoPorNivel()).isEmpty();
        assertThat(TipoDeRegra.PRIORIDADE_POR_DATA.rigidezPadrao()).isEqualTo(Rigidez.MEDIUM);
        assertThat(TipoDeRegra.EQUILIBRIO_DE_CARGA.rigidezPadrao()).isEqualTo(Rigidez.SOFT);
    }

    @Test
    void soOLimiteOMinimoPorNivelEOEquilibrioSeDesligam() {
        assertThat(Arrays.stream(TipoDeRegra.values()).filter(tipo -> !tipo.sempreAtiva()))
                .containsExactly(
                        TipoDeRegra.LIMITE_POR_PERIODO,
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO,
                        TipoDeRegra.EQUILIBRIO_DE_CARGA);
    }

    @Test
    void oPadraoDeCadaTipoVaiEVoltaDoJson() {
        for (TipoDeRegra tipo : TipoDeRegra.values()) {
            String json = ParametrosEmJson.escrever(tipo.parametrosPadrao());
            assertThat(ParametrosEmJson.ler(json, tipo.classeDosParametros()))
                    .as(tipo.name())
                    .isEqualTo(tipo.parametrosPadrao());
        }
    }
}
