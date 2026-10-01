package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/**
 * O painel do gerente num mês: quem serve, as respostas a cada evento por vir e quem ainda falta.
 *
 * @param temPeriodo o mês já tem eventos (e por isso uma trava para ligar ou desligar)
 * @param membros quem falta responder primeiro, depois em ordem de nome
 */
public record PainelDaDisponibilidade(
        YearMonth mes, boolean temPeriodo, boolean travado, List<EventoDoPainel> eventos, List<LinhaDoPainel> membros) {

    public List<LinhaDoPainel> faltamResponder() {
        return membros.stream().filter(membro -> !membro.respondeuTudo()).toList();
    }

    public List<LinhaDoPainel> responderam() {
        return membros.stream().filter(LinhaDoPainel::respondeuTudo).toList();
    }

    public long semNenhumaResposta() {
        return membros.stream().filter(LinhaDoPainel::semNenhumaResposta).count();
    }

    /** "novembro". */
    public String nomeDoMes() {
        return Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
    }
}
