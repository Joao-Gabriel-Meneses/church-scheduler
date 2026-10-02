package br.igreja.escala.escala.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/** Peças do problema para os testes do solver, em outubro de 2026, sem banco. */
final class Cenario {

    static final FuncaoDaEscala PROJECAO = new FuncaoDaEscala(100L, "Projeção", 1, 1);
    static final FuncaoDaEscala TRANSMISSAO = new FuncaoDaEscala(101L, "Transmissão", 1, 1);
    static final Long INICIANTE = 200L;
    static final Long EXPERIENTE = 201L;

    private Cenario() {}

    /** Evento em outubro de 2026, no dia e horário dados, com a duração em minutos. */
    static EventoDaEscala evento(long id, int dia, int hora, int minuto, int minutos) {
        var inicio = LocalDateTime.of(LocalDate.of(2026, 10, dia), LocalTime.of(hora, minuto));
        return new EventoDaEscala(id, "Evento " + id, inicio, inicio.plusMinutes(minutos));
    }

    /** Culto de duas horas às 18h00 no dia. */
    static EventoDaEscala culto(long id, int dia) {
        return evento(id, dia, 18, 0, 120);
    }

    /** Habilitada nas duas funções como experiente e disponível nos eventos dados. */
    static Pessoa pessoa(long id, Long... eventosQuePode) {
        return new Pessoa(
                id,
                "Pessoa " + id,
                Map.of(PROJECAO.id(), EXPERIENTE, TRANSMISSAO.id(), EXPERIENTE),
                Set.of(eventosQuePode));
    }

    static Pessoa pessoa(long id, Map<Long, Long> nivelPorFuncao, Long... eventosQuePode) {
        return new Pessoa(id, "Pessoa " + id, nivelPorFuncao, Set.of(eventosQuePode));
    }

    static VagaPlanejada vaga(long id, EventoDaEscala evento, FuncaoDaEscala funcao, int posicao, Pessoa pessoa) {
        return new VagaPlanejada(id, evento, funcao, posicao, false, pessoa);
    }

    static VagaPlanejada vaga(long id, EventoDaEscala evento, FuncaoDaEscala funcao, Pessoa pessoa) {
        return vaga(id, evento, funcao, 1, pessoa);
    }

    static ParametrosDaEscala limite(int limitePorMes) {
        return new ParametrosDaEscala(limitePorMes, null, 1);
    }

    static ParametrosDaEscala maximoDeIniciantes(int maximo) {
        return new ParametrosDaEscala(3, INICIANTE, maximo);
    }
}
