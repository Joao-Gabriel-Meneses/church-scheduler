package br.igreja.escala.escala.service;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.CompromissoFixo;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tudo o que a escala de um mês usa, lido dos outros módulos numa transação (LeituraDoPeriodo). As entidades vêm com o
 * que a escala lê já carregado (as funções exigidas de cada evento).
 *
 * @param periodo nulo se o mês ainda não tem eventos
 * @param eventos do período, cancelados incluídos, por data
 * @param vagas dos eventos do período
 * @param quemServe quem serve no ministério (conta ativa e alguma habilitação)
 * @param nomesDeQuemNaoServe quem está numa vaga e não serve mais (removido, desativado ou sem habilitação)
 * @param quemPode por evento por vir, quem serve e marcou Pode
 * @param compromissosEmOutrosMinisterios vagas vigentes de outros ministérios de quem serve aqui
 * @param agora o relógio de São Paulo: o que começou antes dele fica como está
 */
record DadosDoPeriodo(
        Long ministerioId,
        String nomeDoMinisterio,
        YearMonth mes,
        Periodo periodo,
        List<Evento> eventos,
        List<Funcao> funcoes,
        List<Vaga> vagas,
        List<UsuarioResumo> quemServe,
        Map<Long, String> nomesDeQuemNaoServe,
        Map<Long, Set<Long>> quemPode,
        List<HabilitacaoDaPessoa> habilitacoes,
        Map<Long, String> nomesDosNiveis,
        RegrasDoMinisterio regras,
        List<CompromissoFixo> compromissosEmOutrosMinisterios,
        LocalDateTime agora) {

    DadosDoPeriodo comVagas(List<Vaga> outras) {
        return new DadosDoPeriodo(
                ministerioId,
                nomeDoMinisterio,
                mes,
                periodo,
                eventos,
                funcoes,
                outras,
                quemServe,
                nomesDeQuemNaoServe,
                quemPode,
                habilitacoes,
                nomesDosNiveis,
                regras,
                compromissosEmOutrosMinisterios,
                agora);
    }

    boolean porVir(Evento evento) {
        return evento.getInicio().isAfter(agora);
    }
}
