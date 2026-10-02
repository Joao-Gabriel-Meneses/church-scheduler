package br.igreja.escala.escala.solver;

import java.time.LocalDateTime;

/**
 * Onde a pessoa já serve e a geração não mexe: vagas preenchidas de outros ministérios (só pessoa e horário, sem dizer
 * qual ministério nem qual evento) e vagas deste ministério em eventos do mês que já começaram ({@code contaNoPeriodo}:
 * entram no limite e no equilíbrio).
 */
public record CompromissoFixo(
        Long pessoaId, Long eventoId, LocalDateTime inicio, LocalDateTime fim, boolean contaNoPeriodo) {}
