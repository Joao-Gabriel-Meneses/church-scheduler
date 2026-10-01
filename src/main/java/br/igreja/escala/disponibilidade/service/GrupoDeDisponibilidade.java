package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

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

    /** "Nenhum evento por vir em novembro." */
    public String semEventos() {
        return "Nenhum evento por vir em " + Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT) + ".";
    }

    /** "4 de 5 respondidos". */
    public String contagem() {
        return TelaDaDisponibilidade.contagem(respondidos(), linhas.size());
    }
}
