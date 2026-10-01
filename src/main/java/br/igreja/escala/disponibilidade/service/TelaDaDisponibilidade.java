package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * A tela de disponibilidade de uma pessoa num mês: um grupo por ministério em que ela serve, em ordem de nome.
 *
 * @param serveEmAlgum a pessoa tem habilitação em algum ministério (senão a tela explica que falta o gerente)
 */
public record TelaDaDisponibilidade(YearMonth mes, boolean serveEmAlgum, List<GrupoDeDisponibilidade> grupos) {

    public long respondidos() {
        return grupos.stream().mapToLong(GrupoDeDisponibilidade::respondidos).sum();
    }

    public int total() {
        return grupos.stream().mapToInt(grupo -> grupo.linhas().size()).sum();
    }

    /** "7 de 13 respondidos", somando todos os ministérios. */
    public String contagem() {
        return contagem(respondidos(), total());
    }

    /** "Mídia — Novembro" com um ministério; "Disponibilidade — Novembro" com vários ou nenhum. */
    public String titulo() {
        String nome = grupos.size() == 1 ? grupos.get(0).ministerio() : "Disponibilidade";
        return nome + " — " + Datas.nomeDoMes(mes);
    }

    public Optional<GrupoDeDisponibilidade> grupo(Long ministerioId) {
        return grupos.stream()
                .filter(grupo -> grupo.ministerioId().equals(ministerioId))
                .findFirst();
    }

    static String contagem(long respondidos, int total) {
        return respondidos + " de " + total + (total == 1 ? " respondido" : " respondidos");
    }
}
