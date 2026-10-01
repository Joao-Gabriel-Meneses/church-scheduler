package br.igreja.escala.disponibilidade.service;

import java.time.YearMonth;
import java.util.List;

/**
 * Os eventos por vir de um ministério no mês, para uma pessoa marcar.
 *
 * @param tint cor da etiqueta do ministério (opção {@code tint} do Badge)
 * @param travado a disponibilidade do mês está travada
 * @param editavel quem está vendo pode marcar: a pessoa até a trava; o gerente, sempre
 */
public record GrupoDeDisponibilidade(
        Long ministerioId,
        String ministerio,
        String tint,
        YearMonth mes,
        boolean travado,
        boolean editavel,
        List<LinhaDeDisponibilidade> linhas) {

    public long respondidos() {
        return linhas.stream().filter(LinhaDeDisponibilidade::respondida).count();
    }

    /** "4 de 5 respondidos". */
    public String contagem() {
        return TelaDaDisponibilidade.contagem(respondidos(), linhas.size());
    }
}
