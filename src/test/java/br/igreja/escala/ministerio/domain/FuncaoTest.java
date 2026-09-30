package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class FuncaoTest {

    private final Ministerio midia = new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR);

    @Test
    void criaComQuantidadeDePessoasPorEvento() {
        var projecao = new Funcao(midia, " Projeção ", Icone.MONITOR, 1, 1);

        assertThat(projecao.getNome()).isEqualTo("Projeção");
        assertThat(projecao.getMinisterio()).isSameAs(midia);
        assertThat(projecao.getQtdMin()).isEqualTo(1);
        assertThat(projecao.getQtdMax()).isEqualTo(1);
    }

    @Test
    void aceitaFuncaoOpcionalComMinimoZero() {
        var apoio = new Funcao(midia, "Apoio", Icone.USERS, 0, 2);

        assertThat(apoio.getQtdMin()).isZero();
    }

    @Test
    void recusaMaximoMenorQueOMinimoZeroOuAcimaDoTeto() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Funcao(midia, "Projeção", Icone.MONITOR, 2, 1))
                .withMessageContaining("qtdMin");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Funcao(midia, "Projeção", Icone.MONITOR, 0, 0))
                .withMessageContaining("qtdMax");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Funcao(midia, "Projeção", Icone.MONITOR, 1, Funcao.QTD_MAXIMA + 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Funcao(midia, "Projeção", Icone.MONITOR, -1, 1));
    }

    @Test
    void alteraNomeIconeEQuantidades() {
        var funcao = new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1);

        funcao.alterar("Transmissão", Icone.VIDEO, 1, 2);

        assertThat(funcao.getNome()).isEqualTo("Transmissão");
        assertThat(funcao.getIcone()).isEqualTo(Icone.VIDEO);
        assertThat(funcao.getQtdMax()).isEqualTo(2);
    }

    @Test
    void exigeMinisterioNomeEIcone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Funcao(null, "Projeção", Icone.MONITOR, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Funcao(midia, "", Icone.MONITOR, 1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new Funcao(midia, "Projeção", null, 1, 1));
    }
}
