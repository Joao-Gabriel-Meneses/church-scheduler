package br.igreja.escala.ministerio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MinisterioTest {

    @Test
    void criaComNomeSemEspacosCorEIcone() {
        var midia = new Ministerio("  Mídia ", CorDoMinisterio.MINT, Icone.MONITOR);

        assertThat(midia.getNome()).isEqualTo("Mídia");
        assertThat(midia.getCor()).isEqualTo(CorDoMinisterio.MINT);
        assertThat(midia.getIcone()).isEqualTo(Icone.MONITOR);
    }

    @Test
    void alteraOsTresCampos() {
        var ministerio = new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR);

        ministerio.alterar("Louvor", CorDoMinisterio.ROSE, Icone.MUSIC);

        assertThat(ministerio.getNome()).isEqualTo("Louvor");
        assertThat(ministerio.getCor()).isEqualTo(CorDoMinisterio.ROSE);
        assertThat(ministerio.getIcone()).isEqualTo(Icone.MUSIC);
    }

    @Test
    void exigeNomeCorEIcone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Ministerio(" ", CorDoMinisterio.MINT, Icone.MONITOR))
                .withMessageContaining("nome");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Ministerio("Mídia", null, Icone.MONITOR))
                .withMessageContaining("cor");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Ministerio("Mídia", CorDoMinisterio.MINT, null))
                .withMessageContaining("icone");
    }

    @Test
    void mesmoQueComparaPelaInstanciaOuPeloId() {
        var midia = comId(1L);
        var outraCopiaDaMidia = comId(1L);
        var louvor = comId(2L);
        var semId = new Ministerio("Novo", CorDoMinisterio.LEMON, Icone.USERS);

        assertThat(midia.mesmoQue(midia)).isTrue();
        assertThat(midia.mesmoQue(outraCopiaDaMidia)).isTrue();
        assertThat(midia.mesmoQue(louvor)).isFalse();
        assertThat(midia.mesmoQue(null)).isFalse();
        assertThat(semId.mesmoQue(new Ministerio("Novo", CorDoMinisterio.LEMON, Icone.USERS)))
                .isFalse();
    }

    static Ministerio comId(Long id) {
        var ministerio = new Ministerio("Ministério " + id, CorDoMinisterio.MINT, Icone.MONITOR);
        ReflectionTestUtils.setField(ministerio, "id", id);
        return ministerio;
    }
}
