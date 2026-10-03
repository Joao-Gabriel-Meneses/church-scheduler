package br.igreja.escala.escala.service;

import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.CompromissoFixo;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.EventoDaEscala;
import br.igreja.escala.escala.solver.FuncaoDaEscala;
import br.igreja.escala.escala.solver.ParametrosDaEscala;
import br.igreja.escala.escala.solver.PesosDasRegras;
import br.igreja.escala.escala.solver.Pessoa;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Do que foi lido do banco para o problema do solver, sem banco nem Spring. Só os eventos por vir entram; o que já
 * começou fica como está e conta no mês como compromisso fixo.
 */
final class MontagemDaEscala {

    private MontagemDaEscala() {}

    /** Vaga que vale na escala: evento não cancelado, função exigida por ele e posição até o máximo da função. */
    static boolean vigente(Vaga vaga, Evento evento, Funcao funcao) {
        return evento != null
                && funcao != null
                && !evento.isCancelado()
                && evento.exige(funcao.getId())
                && vaga.getPosicao() <= funcao.getQtdMax();
    }

    /**
     * As vagas que faltam nos eventos por vir (posição 1 até o máximo de cada função exigida) e as que saem: as dos
     * eventos cancelados e as que deixaram de valer nos eventos por vir. Evento que já começou não muda.
     */
    static Reconciliacao reconciliar(DadosDoPeriodo dados) {
        var eventos = porId(dados.eventos(), Evento::getId);
        var funcoes = porId(dados.funcoes(), Funcao::getId);
        Set<String> existentes =
                dados.vagas().stream().map(MontagemDaEscala::chave).collect(Collectors.toSet());
        var novas = new ArrayList<Vaga>();
        for (Evento evento : dados.eventos()) {
            if (evento.isCancelado() || !dados.porVir(evento)) {
                continue;
            }
            for (Funcao funcao : dados.funcoes()) {
                if (!evento.exige(funcao.getId())) {
                    continue;
                }
                for (int posicao = 1; posicao <= funcao.getQtdMax(); posicao++) {
                    if (!existentes.contains(chave(evento.getId(), funcao.getId(), posicao))) {
                        novas.add(new Vaga(evento.getId(), funcao.getId(), posicao));
                    }
                }
            }
        }
        var apagar = dados.vagas().stream()
                .filter(vaga -> {
                    var evento = eventos.get(vaga.getEventoId());
                    return evento != null
                            && (evento.isCancelado()
                                    || (dados.porVir(evento)
                                            && !vigente(vaga, evento, funcoes.get(vaga.getFuncaoId()))));
                })
                .toList();
        return new Reconciliacao(novas, apagar);
    }

    /**
     * O problema do mês. Com {@code recomecar}, as vagas que não estão presas começam vazias (gerar de novo substitui o
     * rascunho); sem ele, ficam como estão (a página explica as vazias).
     */
    static EscalaDoPeriodo montar(DadosDoPeriodo dados, boolean recomecar) {
        var funcoes = porId(dados.funcoes(), Funcao::getId);
        var pessoas = pessoas(dados);
        var pessoaPorId = porId(pessoas, Pessoa::id);
        Map<Long, EventoDaEscala> eventosPorVir = new LinkedHashMap<>();
        var compromissos = new ArrayList<>(dados.compromissosEmOutrosMinisterios());
        var eventos = porId(dados.eventos(), Evento::getId);
        var vagas = new ArrayList<VagaPlanejada>();
        for (Vaga vaga : ordenadas(dados)) {
            var evento = eventos.get(vaga.getEventoId());
            var funcao = funcoes.get(vaga.getFuncaoId());
            if (!vigente(vaga, evento, funcao)) {
                continue;
            }
            if (!dados.porVir(evento)) {
                if (!vaga.isVazia()) {
                    compromissos.add(new CompromissoFixo(
                            vaga.getUsuarioId(), evento.getId(), evento.getInicio(), evento.getFim(), true));
                }
                continue;
            }
            var doEvento = eventosPorVir.computeIfAbsent(
                    evento.getId(),
                    id -> new EventoDaEscala(id, evento.getNome(), evento.getInicio(), evento.getFim()));
            var pessoa = recomecar && !vaga.isPresa() ? null : pessoaPorId.get(vaga.getUsuarioId());
            vagas.add(new VagaPlanejada(
                    vaga.getId(),
                    doEvento,
                    new FuncaoDaEscala(funcao.getId(), funcao.getNome(), funcao.getQtdMin(), funcao.getQtdMax()),
                    vaga.getPosicao(),
                    vaga.isPresa(),
                    pessoa));
        }
        return new EscalaDoPeriodo(
                dados.periodo().getId(),
                pessoas,
                compromissos,
                ParametrosDaEscala.de(dados.regras()),
                vagas,
                PesosDasRegras.de(dados.regras()));
    }

    /**
     * Quem serve, com o nível em cada função e os eventos em que marcou Pode; e quem está numa vaga mas não serve mais,
     * sem habilitação nem disponibilidade (as regras rígidas a tiram ao gerar de novo).
     */
    private static List<Pessoa> pessoas(DadosDoPeriodo dados) {
        Map<Long, Map<Long, Long>> niveis = dados.habilitacoes().stream()
                .collect(Collectors.groupingBy(
                        HabilitacaoDaPessoa::usuarioId,
                        Collectors.toMap(HabilitacaoDaPessoa::funcaoId, HabilitacaoDaPessoa::nivelId)));
        Map<Long, Set<Long>> eventosQuePode = new LinkedHashMap<>();
        dados.quemPode()
                .forEach((eventoId, ids) -> ids.forEach(id -> eventosQuePode
                        .computeIfAbsent(id, chave -> new HashSet<>())
                        .add(eventoId)));
        var pessoas = new ArrayList<Pessoa>();
        for (UsuarioResumo usuario : dados.quemServe()) {
            pessoas.add(new Pessoa(
                    usuario.id(),
                    usuario.nome(),
                    niveis.getOrDefault(usuario.id(), Map.of()),
                    eventosQuePode.getOrDefault(usuario.id(), Set.of())));
        }
        dados.quemNaoServeMais()
                .forEach(usuario -> pessoas.add(new Pessoa(usuario.id(), usuario.nome(), Map.of(), Set.of())));
        return pessoas;
    }

    private static List<Vaga> ordenadas(DadosDoPeriodo dados) {
        Map<Long, Integer> ordemDoEvento = new LinkedHashMap<>();
        dados.eventos().forEach(evento -> ordemDoEvento.put(evento.getId(), ordemDoEvento.size()));
        Map<Long, Integer> ordemDaFuncao = new LinkedHashMap<>();
        dados.funcoes().forEach(funcao -> ordemDaFuncao.put(funcao.getId(), ordemDaFuncao.size()));
        return dados.vagas().stream()
                .sorted(Comparator.comparing(
                                (Vaga vaga) -> ordemDoEvento.getOrDefault(vaga.getEventoId(), Integer.MAX_VALUE))
                        .thenComparing(vaga -> ordemDaFuncao.getOrDefault(vaga.getFuncaoId(), Integer.MAX_VALUE))
                        .thenComparing(Vaga::getPosicao))
                .toList();
    }

    private static String chave(Vaga vaga) {
        return chave(vaga.getEventoId(), vaga.getFuncaoId(), vaga.getPosicao());
    }

    private static String chave(Long eventoId, Long funcaoId, int posicao) {
        return eventoId + "/" + funcaoId + "/" + posicao;
    }

    private static <T> Map<Long, T> porId(List<T> itens, Function<T, Long> id) {
        return itens.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** O que a geração muda nas vagas antes de resolver. */
    record Reconciliacao(List<Vaga> novas, List<Vaga> apagar) {}
}
