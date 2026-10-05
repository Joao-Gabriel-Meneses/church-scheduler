package br.igreja.escala.disponibilidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.web.ResumoDaDisponibilidade;
import br.igreja.escala.evento.service.EventoService;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisponibilidadeNoInicioDoMembroTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final ConsultaDaDisponibilidade consulta = mock(ConsultaDaDisponibilidade.class);
    private final EventoService eventos = mock(EventoService.class);
    private final DisponibilidadeNoInicioDoMembro resumo = new DisponibilidadeNoInicioDoMembro(consulta, eventos);

    @Test
    void resumeOProximoMesComUmaLinhaPorMinisterio() {
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(consulta.doMembro(7L, NOVEMBRO))
                .thenReturn(new TelaDaDisponibilidade(
                        NOVEMBRO,
                        true,
                        List.of(
                                new GrupoDeDisponibilidade(1L, "Mídia", "mint", NOVEMBRO, false, true, List.of()),
                                new GrupoDeDisponibilidade(2L, "Louvor", "rose", NOVEMBRO, true, false, List.of()))));

        var doMembro = resumo.doMembro(7L);

        assertThat(doMembro.mes()).isEqualTo("Novembro");
        assertThat(doMembro.url()).isEqualTo("/disponibilidade?mes=2026-11");
        assertThat(doMembro.ministerios())
                .extracting(ResumoDaDisponibilidade.Ministerio::nome, ResumoDaDisponibilidade.Ministerio::resumo)
                .containsExactly(tuple("Mídia", "0 de 0 respondidos"), tuple("Louvor", "Travada · 0 de 0 respondidos"));
    }

    @Test
    void quemNaoServeEmNenhumMinisterioFicaSemLinhas() {
        when(eventos.proximoMes()).thenReturn(NOVEMBRO);
        when(consulta.doMembro(7L, NOVEMBRO)).thenReturn(new TelaDaDisponibilidade(NOVEMBRO, false, List.of()));

        assertThat(resumo.doMembro(7L).ministerios()).isEmpty();
    }
}
