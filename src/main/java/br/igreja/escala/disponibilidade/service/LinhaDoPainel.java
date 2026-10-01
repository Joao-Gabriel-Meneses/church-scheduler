package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.identidade.service.UsuarioResumo;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Uma pessoa que serve no ministério, com a resposta a cada evento por vir do mês, na ordem das colunas do painel.
 *
 * @param respostas uma por evento; nula onde a pessoa não respondeu
 */
public record LinhaDoPainel(UsuarioResumo pessoa, List<Resposta> respostas) {

    public LinhaDoPainel {
        respostas = Collections.unmodifiableList(respostas);
    }

    public long respondidas() {
        return respostas.stream().filter(Objects::nonNull).count();
    }

    /** Respondeu todos os eventos por vir. Um evento novo volta a pessoa para pendente. */
    public boolean respondeuTudo() {
        return !respostas.isEmpty() && respondidas() == respostas.size();
    }

    public boolean semNenhumaResposta() {
        return respondidas() == 0;
    }

    public long podeEm() {
        return respostas.stream().filter(Resposta.PODE::equals).count();
    }

    /** "Respondeu", "Faltam 2" ou "Sem resposta" (DataTable, coluna Estado). */
    public String estado() {
        if (respondeuTudo()) {
            return "Respondeu";
        }
        if (semNenhumaResposta()) {
            return "Sem resposta";
        }
        long faltam = respostas.size() - respondidas();
        return faltam == 1 ? "Falta 1" : "Faltam " + faltam;
    }

    /** Opção {@code variante} do estado (componentes/tabela): ok, vazio ou alerta. */
    public String varianteDoEstado() {
        if (respondeuTudo()) {
            return null;
        }
        return semNenhumaResposta() ? "alerta" : "vazio";
    }

    /** "Pode em 5 de 9" para quem respondeu tudo; "Respondeu 3 de 9" ou "Sem resposta" para quem falta. */
    public String resumo() {
        if (respondeuTudo()) {
            return "Pode em " + podeEm() + " de " + respostas.size();
        }
        if (semNenhumaResposta()) {
            return "Sem resposta";
        }
        return "Respondeu " + respondidas() + " de " + respostas.size();
    }
}
