package br.igreja.escala.escala.service;

import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.domain.Funcao;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Quem está escalado em cada função de um evento, como a escala está agora (com as desistências e os ajustes depois da
 * publicação). É o que se compartilha: o texto do WhatsApp e o PDF do mês.
 *
 * @param quando "12/10 · Dom · 18h00"
 * @param funcoes uma por função do ministério, na ordem das colunas da grade
 */
public record EscaladosDoEvento(
        Long eventoId, LocalDateTime inicio, String quando, String nome, List<NaFuncao> funcoes) {

    /** A vaga obrigatória vazia, no texto e no PDF. */
    public static final String A_DEFINIR = "a definir";

    public EscaladosDoEvento {
        funcoes = List.copyOf(funcoes);
    }

    /**
     * Uma função no evento.
     *
     * @param exigida se o evento precisa da função; sem ela, a lista é vazia
     * @param pessoas os nomes por posição, com "a definir" no lugar da vaga obrigatória vazia (a opcional vazia não
     *     aparece); função exigida sem ninguém tem pelo menos um "a definir"
     */
    public record NaFuncao(String funcao, boolean exigida, List<String> pessoas) {

        public NaFuncao {
            pessoas = List.copyOf(pessoas);
        }
    }

    /** Os eventos não cancelados do mês, em ordem de início (data e horário), com quem está em cada função. */
    static List<EscaladosDoEvento> doMes(DadosDoPeriodo dados) {
        Map<Long, String> nomes = Stream.of(dados.quemServe(), dados.quemNaoServeMais())
                .flatMap(List::stream)
                .collect(Collectors.toMap(UsuarioResumo::id, UsuarioResumo::nome, (a, b) -> a));
        Map<Long, Funcao> funcoes =
                dados.funcoes().stream().collect(Collectors.toMap(Funcao::getId, Function.identity()));
        var eventos = dados.eventos().stream()
                .filter(evento -> !evento.isCancelado())
                .sorted(Comparator.comparing(Evento::getInicio).thenComparing(Evento::getId))
                .toList();
        var doMes = new ArrayList<EscaladosDoEvento>();
        for (Evento evento : eventos) {
            var porFuncao = new ArrayList<NaFuncao>();
            for (Funcao funcao : dados.funcoes()) {
                if (!evento.exige(funcao.getId())) {
                    porFuncao.add(new NaFuncao(funcao.getNome(), false, List.of()));
                    continue;
                }
                var pessoas = dados.vagas().stream()
                        .filter(vaga -> vaga.getEventoId().equals(evento.getId())
                                && vaga.getFuncaoId().equals(funcao.getId())
                                && MontagemDaEscala.vigente(vaga, evento, funcoes.get(vaga.getFuncaoId())))
                        .sorted(Comparator.comparing(Vaga::getPosicao))
                        .filter(vaga -> !vaga.isVazia() || vaga.getPosicao() <= funcao.getQtdMin())
                        .map(vaga ->
                                vaga.isVazia() ? A_DEFINIR : nomes.getOrDefault(vaga.getUsuarioId(), "Pessoa removida"))
                        .toList();
                porFuncao.add(new NaFuncao(funcao.getNome(), true, pessoas.isEmpty() ? List.of(A_DEFINIR) : pessoas));
            }
            doMes.add(new EscaladosDoEvento(
                    evento.getId(),
                    evento.getInicio(),
                    AjusteDaEscala.quando(evento.getInicio()),
                    evento.getNome(),
                    porFuncao));
        }
        return doMes;
    }
}
