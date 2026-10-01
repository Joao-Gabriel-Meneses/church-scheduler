package br.igreja.escala.ministerio;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.domain.Nivel;
import org.springframework.test.util.ReflectionTestUtils;

/** Entidades do ministério com id, como se viessem do banco, para testes sem banco. */
public final class Exemplos {

    private Exemplos() {}

    public static Ministerio midia() {
        return comId(new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR), AcessoDeTeste.MIDIA);
    }

    public static Ministerio louvor() {
        return comId(new Ministerio("Louvor", CorDoMinisterio.ROSE, Icone.MUSIC), AcessoDeTeste.LOUVOR);
    }

    public static Funcao projecao(Ministerio ministerio) {
        return comId(new Funcao(ministerio, "Projeção", Icone.MONITOR, 1, 1), 100L);
    }

    public static Funcao transmissao(Ministerio ministerio) {
        return comId(new Funcao(ministerio, "Transmissão", Icone.VIDEO, 1, 1), 101L);
    }

    public static Nivel iniciante(Ministerio ministerio) {
        return comId(new Nivel(ministerio, "Iniciante", 1), 200L);
    }

    public static Nivel experiente(Ministerio ministerio) {
        return comId(new Nivel(ministerio, "Experiente", 2), 201L);
    }

    public static <T> T comId(T entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }
}
