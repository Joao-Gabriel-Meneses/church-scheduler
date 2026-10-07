package br.igreja.escala.escala.web;

import br.igreja.escala.escala.service.AlertaDaEscala;
import br.igreja.escala.escala.service.CargaDaPessoa;
import br.igreja.escala.escala.service.CelulaDaGrade;
import br.igreja.escala.escala.service.LinhaDaGrade;
import br.igreja.escala.escala.service.PaginaDaEscala;
import br.igreja.escala.escala.service.ResumoDaEscala;
import br.igreja.escala.escala.service.SlotDaGrade;
import java.time.YearMonth;
import java.util.List;

/**
 * A página de escalas de exemplo para os testes de controller: um culto em 01/11 com a Ana (vaga 7) em Projeção e
 * Transmissão vazia (vaga 8).
 */
final class PaginasDeExemplo {

    private PaginasDeExemplo() {}

    static PaginaDaEscala rascunho(YearMonth mes, boolean travada, boolean ajustavel) {
        return pagina(mes, travada, false, ajustavel);
    }

    static PaginaDaEscala publicada(YearMonth mes) {
        return pagina(mes, true, true, true);
    }

    /** O mês tem eventos, mas a escala ainda não foi gerada. */
    static PaginaDaEscala naoGerada(YearMonth mes) {
        return new PaginaDaEscala(
                mes,
                true,
                true,
                false,
                "Rascunho",
                false,
                false,
                List.of("Projeção", "Transmissão"),
                List.of(),
                List.of(),
                new ResumoDaEscala(0, 0, 0, 1, 0, List.of()));
    }

    static PaginaDaEscala semPeriodo(YearMonth mes) {
        return new PaginaDaEscala(
                mes,
                false,
                false,
                false,
                null,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                new ResumoDaEscala(0, 0, 0, 0, 0, List.of()));
    }

    private static PaginaDaEscala pagina(YearMonth mes, boolean travada, boolean publicada, boolean ajustavel) {
        var linha = new LinhaDaGrade(
                "01",
                "Dom",
                "Culto de domingo",
                "18h00",
                true,
                List.of(
                        new CelulaDaGrade(
                                "Projeção",
                                true,
                                List.of(SlotDaGrade.de(
                                        7L, 2, "Ana Souza", "Experiente", false, false, null, null, ajustavel))),
                        new CelulaDaGrade(
                                "Transmissão", true, List.of(SlotDaGrade.vazia(8L, 0, true, false, ajustavel)))),
                publicada
                        ? new LinhaDaGrade.Whatsapp(
                                "whatsapp-501",
                                "*Mídia — Culto de domingo*\n01/11 · Dom · 18h00\n\nProjeção: Ana Souza\n"
                                        + "Transmissão: a definir")
                        : null);
        return new PaginaDaEscala(
                mes,
                true,
                travada,
                true,
                publicada ? "Publicada" : "Rascunho",
                publicada,
                ajustavel,
                List.of("Projeção", "Transmissão"),
                List.of(linha),
                publicada
                        ? List.of(new AlertaDaEscala(
                                "Bruno Lima desistiu de Transmissão, 01/11 · Dom · 18h00 · Culto de domingo",
                                "A vaga está vazia desde 28/10 às 14h32. Escolha quem entra no lugar: o ajuste"
                                        + " confere as regras.",
                                null,
                                "arrow-left-right",
                                8L))
                        : List.of(new AlertaDaEscala(
                                "Transmissão, 01/11 · Dom · 18h00 · Culto de domingo",
                                "Ninguém habilitado em Transmissão marcou Pode.",
                                "Regra: DISPONIBILIDADE")),
                new ResumoDaEscala(2, 1, 1, 1, 1, List.of(new CargaDaPessoa("Ana Souza", "AS", 1))));
    }
}
