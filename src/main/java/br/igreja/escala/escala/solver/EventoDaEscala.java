package br.igreja.escala.escala.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** Um evento por vir do período, com início e fim (que pode cair no dia seguinte). */
public record EventoDaEscala(Long id, String nome, LocalDateTime inicio, LocalDateTime fim) {

    public LocalDate data() {
        return inicio.toLocalDate();
    }

    /** Quantos dias faltam do evento até o fim do mês: o evento mais perto do início do mês tem mais. */
    public int diasAteOFimDoMes() {
        return YearMonth.from(inicio).lengthOfMonth() - inicio.getDayOfMonth();
    }
}
