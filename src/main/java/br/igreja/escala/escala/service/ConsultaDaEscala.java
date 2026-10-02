package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.DiagnosticoDaVaga;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.StatusDaEscala;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A página de escalas: a grade do mês (só leitura nesta fase), os alertas (vagas vazias explicadas e quem saiu da
 * escala) e o resumo. A explicação é calculada na hora, com os dados de agora.
 */
@Service
public class ConsultaDaEscala {

    private final LeituraDoPeriodo leitura;

    ConsultaDaEscala(LeituraDoPeriodo leitura) {
        this.leitura = leitura;
    }

    /**
     * @throws br.igreja.escala.compartilhado.NaoEncontradoException se o ministério não existe
     */
    @Transactional(readOnly = true)
    public PaginaDaEscala doMes(Long ministerioId, YearMonth mes) {
        return montar(leitura.ler(ministerioId, mes));
    }

    static PaginaDaEscala montar(DadosDoPeriodo dados) {
        if (dados.periodo() == null) {
            return PaginaDaEscala.semPeriodo(dados.mes());
        }
        var funcoes = porId(dados.funcoes(), Funcao::getId);
        var eventos = porId(dados.eventos(), Evento::getId);
        var pessoas = porId(
                Stream.concat(dados.quemServe().stream(), dados.quemNaoServeMais().stream())
                        .toList(),
                UsuarioResumo::id);
        Map<String, Long> niveis = new HashMap<>();
        for (HabilitacaoDaPessoa habilitacao : dados.habilitacoes()) {
            niveis.put(habilitacao.usuarioId() + "/" + habilitacao.funcaoId(), habilitacao.nivelId());
        }
        var vigentes = dados.vagas().stream()
                .filter(vaga -> MontagemDaEscala.vigente(
                        vaga, eventos.get(vaga.getEventoId()), funcoes.get(vaga.getFuncaoId())))
                .toList();

        var linhas = new ArrayList<LinhaDaGrade>();
        for (Evento evento : dados.eventos()) {
            if (evento.isCancelado()) {
                continue;
            }
            var celulas = new ArrayList<CelulaDaGrade>();
            boolean comVagaVazia = false;
            for (Funcao funcao : dados.funcoes()) {
                var slots = vigentes.stream()
                        .filter(vaga -> vaga.getEventoId().equals(evento.getId())
                                && vaga.getFuncaoId().equals(funcao.getId()))
                        .sorted(Comparator.comparing(Vaga::getPosicao))
                        .map(vaga -> slot(vaga, funcao, pessoas, niveis, dados.nomesDosNiveis()))
                        .toList();
                comVagaVazia |=
                        dados.porVir(evento) && slots.stream().anyMatch(slot -> slot.vazia() && slot.obrigatoria());
                celulas.add(new CelulaDaGrade(funcao.getNome(), evento.exige(funcao.getId()), slots));
            }
            linhas.add(new LinhaDaGrade(
                    Datas.dia(evento.getData()),
                    Datas.diaDaSemanaCurto(evento.getData().getDayOfWeek()),
                    evento.getNome(),
                    Datas.horario(evento.getHorario()),
                    comVagaVazia,
                    celulas));
        }

        var alertas = new ArrayList<Alerta>();
        alertas.addAll(vagasVazias(dados));
        alertas.addAll(quemSaiu(dados, eventos, funcoes, pessoas));
        alertas.sort(Comparator.comparing(Alerta::quando));

        var periodo = dados.periodo();
        return new PaginaDaEscala(
                dados.mes(),
                true,
                periodo.isDisponibilidadeTravada(),
                !vigentes.isEmpty(),
                periodo.getStatusDaEscala() == StatusDaEscala.PUBLICADA ? "Publicada" : "Rascunho",
                dados.funcoes().stream().map(Funcao::getNome).toList(),
                linhas,
                alertas.stream().map(Alerta::alerta).toList(),
                resumo(dados, vigentes, pessoas));
    }

    private static SlotDaGrade slot(
            Vaga vaga,
            Funcao funcao,
            Map<Long, UsuarioResumo> pessoas,
            Map<String, Long> niveis,
            Map<Long, String> nomesDosNiveis) {
        if (vaga.isVazia()) {
            return SlotDaGrade.vazia(vaga.getPosicao() <= funcao.getQtdMin());
        }
        var pessoa = pessoas.get(vaga.getUsuarioId());
        Long nivel = niveis.get(vaga.getUsuarioId() + "/" + funcao.getId());
        return SlotDaGrade.de(
                pessoa == null ? "Pessoa removida" : pessoa.nome(),
                nivel == null ? null : nomesDosNiveis.get(nivel),
                vaga.isFixada(),
                vaga.isForcada());
    }

    /** As vagas obrigatórias vazias dos eventos por vir, com o motivo (DiagnosticoDaVaga). */
    private static List<Alerta> vagasVazias(DadosDoPeriodo dados) {
        var escala = MontagemDaEscala.montar(dados, false);
        var diagnostico = new DiagnosticoDaVaga(escala, dados.regras(), dados.nomesDosNiveis());
        return escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() == null && vaga.isObrigatoria())
                .map(vaga -> {
                    var motivo = diagnostico.motivo(vaga);
                    var evento = vaga.getEvento();
                    return new Alerta(
                            evento.inicio(),
                            new AlertaDaEscala(
                                    vaga.getFuncao().nome() + ", " + quando(evento.inicio()) + " · " + evento.nome(),
                                    motivo.texto(),
                                    motivo.regra() == null
                                            ? null
                                            : "Regra: " + motivo.regra().name()));
                })
                .toList();
    }

    /**
     * Quem estava escalado num evento por vir e saiu da escala porque o evento foi cancelado ou deixou de exigir a
     * função. As vagas saem de vez na próxima geração.
     */
    private static List<Alerta> quemSaiu(
            DadosDoPeriodo dados,
            Map<Long, Evento> eventos,
            Map<Long, Funcao> funcoes,
            Map<Long, UsuarioResumo> pessoas) {
        Map<Long, List<Vaga>> porEvento = new LinkedHashMap<>();
        for (Vaga vaga : dados.vagas()) {
            var evento = eventos.get(vaga.getEventoId());
            if (!vaga.isVazia()
                    && evento != null
                    && dados.porVir(evento)
                    && !MontagemDaEscala.vigente(vaga, evento, funcoes.get(vaga.getFuncaoId()))) {
                porEvento
                        .computeIfAbsent(evento.getId(), id -> new ArrayList<>())
                        .add(vaga);
            }
        }
        var alertas = new ArrayList<Alerta>();
        porEvento.forEach((eventoId, vagas) -> {
            var evento = eventos.get(eventoId);
            var quem = vagas.stream()
                    .map(vaga -> nome(pessoas.get(vaga.getUsuarioId())) + " ("
                            + (funcoes.containsKey(vaga.getFuncaoId())
                                    ? funcoes.get(vaga.getFuncaoId()).getNome()
                                    : "função excluída")
                            + ")")
                    .toList();
            alertas.add(new Alerta(
                    evento.getInicio(),
                    new AlertaDaEscala(
                            quando(evento.getInicio()) + " · " + evento.getNome()
                                    + (evento.isCancelado() ? " foi cancelado" : " não precisa mais dessas funções"),
                            juntar(quem) + (quem.size() == 1 ? " saiu" : " saíram")
                                    + " da escala. As vagas somem quando você gerar a escala de novo.",
                            null)));
        });
        return alertas;
    }

    private static ResumoDaEscala resumo(DadosDoPeriodo dados, List<Vaga> vigentes, Map<Long, UsuarioResumo> pessoas) {
        Map<Long, Long> eventosPorPessoa = vigentes.stream()
                .filter(vaga -> !vaga.isVazia())
                .collect(Collectors.groupingBy(
                        Vaga::getUsuarioId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(Vaga::getEventoId, Collectors.toSet()),
                                conjunto -> (long) conjunto.size())));
        var porPessoa = new ArrayList<CargaDaPessoa>();
        for (UsuarioResumo pessoa : dados.quemServe()) {
            porPessoa.add(new CargaDaPessoa(
                    pessoa.nome(), pessoa.iniciais(), eventosPorPessoa.getOrDefault(pessoa.id(), 0L)));
        }
        for (UsuarioResumo pessoa : dados.quemNaoServeMais()) {
            if (eventosPorPessoa.containsKey(pessoa.id())) {
                porPessoa.add(new CargaDaPessoa(pessoa.nome(), pessoa.iniciais(), eventosPorPessoa.get(pessoa.id())));
            }
        }
        porPessoa.sort(Comparator.comparing(CargaDaPessoa::escalas)
                .reversed()
                .thenComparing(CargaDaPessoa::nome, String.CASE_INSENSITIVE_ORDER));
        int preenchidas =
                (int) vigentes.stream().filter(vaga -> !vaga.isVazia()).count();
        return new ResumoDaEscala(
                vigentes.size(),
                preenchidas,
                vigentes.size() - preenchidas,
                (int) dados.eventos().stream()
                        .filter(evento -> !evento.isCancelado())
                        .count(),
                porPessoa.stream().mapToLong(CargaDaPessoa::escalas).max().orElse(0),
                porPessoa);
    }

    /** "12/10 · Dom · 18h00". */
    private static String quando(LocalDateTime inicio) {
        return Datas.dataCurta(inicio.toLocalDate()) + " · " + Datas.horario(inicio.toLocalTime());
    }

    private static String nome(UsuarioResumo pessoa) {
        return pessoa == null ? "Pessoa removida" : pessoa.nome();
    }

    /** "Ana", "Ana e Bruno", "Ana, Bruno e Carla". */
    private static String juntar(List<String> partes) {
        if (partes.size() == 1) {
            return partes.getFirst();
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.getLast();
    }

    private static <T> Map<Long, T> porId(List<T> itens, Function<T, Long> id) {
        return itens.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private record Alerta(LocalDateTime quando, AlertaDaEscala alerta) {}
}
