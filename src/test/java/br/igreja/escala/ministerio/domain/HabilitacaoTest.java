package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class HabilitacaoTest {

    private final Ministerio midia = MinisterioTest.comId(1L);
    private final Ministerio louvor = MinisterioTest.comId(2L);
    private final Funcao projecao = new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1);
    private final Nivel iniciante = new Nivel(midia, "Iniciante", 1);
    private final Nivel experiente = new Nivel(midia, "Experiente", 2);

    @Test
    void habilitaOUsuarioNaFuncaoENoNivel() {
        var habilitacao = new Habilitacao(7L, projecao, iniciante);

        assertThat(habilitacao.getUsuarioId()).isEqualTo(7L);
        assertThat(habilitacao.getFuncao()).isSameAs(projecao);
        assertThat(habilitacao.getNivel()).isSameAs(iniciante);
    }

    @Test
    void mudaDeNivelNoMesmoMinisterio() {
        var habilitacao = new Habilitacao(7L, projecao, iniciante);

        habilitacao.mudarNivel(experiente);

        assertThat(habilitacao.getNivel()).isSameAs(experiente);
    }

    @Test
    void recusaNivelDeOutroMinisterio() {
        var solista = new Nivel(louvor, "Solista", 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Habilitacao(7L, projecao, solista))
                .withMessageContaining("mesmo ministério");
        var habilitacao = new Habilitacao(7L, projecao, iniciante);
        assertThatIllegalArgumentException().isThrownBy(() -> habilitacao.mudarNivel(solista));
        assertThat(habilitacao.getNivel()).isSameAs(iniciante);
    }

    @Test
    void exigeUsuarioFuncaoENivel() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Habilitacao(null, projecao, iniciante));
        assertThatIllegalArgumentException().isThrownBy(() -> new Habilitacao(7L, null, iniciante));
        assertThatIllegalArgumentException().isThrownBy(() -> new Habilitacao(7L, projecao, null));
    }
}
