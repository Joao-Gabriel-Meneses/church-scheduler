package br.igreja.escala.disponibilidade.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.compartilhado.EnderecoDoSistema;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.identidade.service.UsuarioResumo;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LembreteDaDisponibilidadeTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final EventoDoPainel CULTO = new EventoDoPainel(500L, "Culto de domingo", "01 Dom", "18h00", 0);

    private final LembreteDaDisponibilidade lembrete =
            new LembreteDaDisponibilidade(new EnderecoDoSistema("https://escala.exemplo.com.br/"));

    @Test
    void textoTemTituloLinkDoMesEQuemAindaFalta() {
        var painel = new PainelDaDisponibilidade(
                NOVEMBRO,
                true,
                false,
                false,
                List.of(CULTO),
                List.of(
                        linha(31L, "Bruno Lima", (Resposta) null),
                        linha(32L, "Carla Dias", (Resposta) null),
                        linha(30L, "Ana Souza", Resposta.PODE)));

        assertThat(lembrete.texto("Mídia", painel)).isEqualTo("""
                        *Mídia — Novembro*
                        A disponibilidade de novembro está aberta. Marque em quais eventos você pode servir: \
                        https://escala.exemplo.com.br/disponibilidade?mes=2026-11

                        Ainda faltam: Bruno Lima, Carla Dias.""");
    }

    @Test
    void semNinguemFaltandoNaoTemALinhaDosNomes() {
        var painel = new PainelDaDisponibilidade(
                NOVEMBRO, true, false, false, List.of(CULTO), List.of(linha(30L, "Ana Souza", Resposta.NAO_PODE)));

        assertThat(lembrete.texto("Louvor", painel))
                .startsWith("*Louvor — Novembro*\n")
                .endsWith("/disponibilidade?mes=2026-11")
                .doesNotContain("Ainda faltam");
    }

    private static LinhaDoPainel linha(Long id, String nome, Resposta... respostas) {
        return new LinhaDoPainel(
                new UsuarioResumo(id, nome, id + "@x.com", null, false, false, true), Arrays.asList(respostas));
    }
}
