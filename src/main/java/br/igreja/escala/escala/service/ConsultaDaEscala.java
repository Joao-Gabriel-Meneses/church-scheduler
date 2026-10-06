package br.igreja.escala.escala.service;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.DiagnosticoDaVaga;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.ValidacaoDaVaga;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A página de escalas: a grade do mês, os alertas (desistências, vagas vazias explicadas, vagas cuja pessoa viola uma
 * regra e quem saiu da escala) e o resumo. A explicação e os avisos são calculados na hora, com os dados de agora; os
 * avisos vêm do solver (ValidacaoDaVaga).
 */
@Service
public class ConsultaDaEscala {

    private final LeituraDoPeriodo leitura;
    private final GeracoesEmAndamento andamentos;
    private final SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager;

    @Autowired
    ConsultaDaEscala(
            LeituraDoPeriodo leitura, GeracoesEmAndamento andamentos, SolverManager<EscalaDoPeriodo> solverManager) {
        this(leitura, andamentos, SolutionManager.create(solverManager));
    }

    ConsultaDaEscala(
            LeituraDoPeriodo leitura,
            GeracoesEmAndamento andamentos,
            SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager) {
        this.leitura = leitura;
        this.andamentos = andamentos;
        this.solutionManager = solutionManager;
    }

    /**
     * @throws br.igreja.escala.compartilhado.NaoEncontradoException se o ministério não existe
     */
    @Transactional(readOnly = true)
    public PaginaDaEscala doMes(Long ministerioId, YearMonth mes) {
        var dados = leitura.ler(ministerioId, mes);
        if (dados.periodo() == null) {
            return montar(dados);
        }
        var escala = MontagemDaEscala.montar(dados, false);
        var avisos = new ValidacaoDaVaga(solutionManager, escala, dados.regras(), dados.nomesDosNiveis()).avisos();
        boolean gerando = andamentos
                .doPeriodo(dados.periodo().getId())
                .filter(Andamento::isGerando)
                .isPresent();
        return montar(dados, avisos, !gerando);
    }

    /** A página do gerente sem avisos e sem ajuste. */
    static PaginaDaEscala montar(DadosDoPeriodo dados) {
        return montar(dados, Map.of(), false);
    }

    /**
     * A grade como o membro a vê: as pessoas e o nível, sem fixada, forçada nem justificativa (isso é do gerente). Os
     * alertas e o resumo vêm junto, mas a página do membro não os mostra.
     */
    static PaginaDaEscala paraOMembro(DadosDoPeriodo dados) {
        return montar(dados, Map.of(), false, false);
    }

    /**
     * @param avisos por vaga, as regras que a pessoa dela viola agora
     * @param ajustavel as vagas por vir se abrem para o ajuste manual
     */
    static PaginaDaEscala montar(DadosDoPeriodo dados, Map<Long, List<Violacao>> avisos, boolean ajustavel) {
        return montar(dados, avisos, ajustavel, true);
    }

    private static PaginaDaEscala montar(
            DadosDoPeriodo dados, Map<Long, List<Violacao>> avisos, boolean ajustavel, boolean doGerente) {
        if (dados.periodo() == null) {
            return PaginaDaEscala.semPeriodo(dados.mes());
        }
        var funcoes = porId(dados.funcoes(), Funcao::getId);
        var eventos = porId(dados.eventos(), Evento::getId);
        var pessoas = porId(
                Stream.of(dados.quemServe(), dados.quemNaoServeMais(), dados.desistentes())
                        .flatMap(List::stream)
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
                        .map(vaga -> slot(
                                vaga,
                                funcao,
                                pessoas,
                                niveis,
                                dados.nomesDosNiveis(),
                                avisos.getOrDefault(vaga.getId(), List.of()),
                                ajustavel && dados.porVir(evento),
                                doGerente))
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
        alertas.addAll(desistencias(dados, eventos, funcoes, pessoas));
        alertas.addAll(vagasVazias(dados));
        alertas.addAll(foraDaRegra(dados, eventos, funcoes, pessoas, avisos));
        alertas.addAll(quemSaiu(dados, eventos, funcoes, pessoas));
        alertas.sort(Comparator.comparing(Alerta::quando));

        var periodo = dados.periodo();
        boolean gerada = !vigentes.isEmpty();
        return new PaginaDaEscala(
                dados.mes(),
                true,
                periodo.isDisponibilidadeTravada(),
                gerada,
                periodo.getStatusDaEscala() == StatusDaEscala.PUBLICADA ? "Publicada" : "Rascunho",
                periodo.isEscalaPublicada(),
                gerada && ajustavel,
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
            Map<Long, String> nomesDosNiveis,
            List<Violacao> avisos,
            boolean editavel,
            boolean doGerente) {
        if (vaga.isVazia()) {
            return SlotDaGrade.vazia(
                    vaga.getId(),
                    vaga.getVersao(),
                    vaga.getPosicao() <= funcao.getQtdMin(),
                    doGerente && vaga.isFixada(),
                    editavel);
        }
        var pessoa = pessoas.get(vaga.getUsuarioId());
        Long nivel = niveis.get(vaga.getUsuarioId() + "/" + funcao.getId());
        return SlotDaGrade.de(
                vaga.getId(),
                vaga.getVersao(),
                pessoa == null ? "Pessoa removida" : pessoa.nome(),
                nivel == null ? null : nomesDosNiveis.get(nivel),
                doGerente && vaga.isFixada(),
                doGerente && vaga.isForcada(),
                doGerente ? vaga.getJustificativa() : null,
                avisos.isEmpty() ? null : regras(avisos),
                editavel);
    }

    /**
     * As vagas dos eventos por vir cuja pessoa viola uma regra rígida como a escala está, sem contar as forçadas (o
     * gerente já justificou): uma alteração em outra vaga, ou uma regra que mudou.
     */
    private static List<Alerta> foraDaRegra(
            DadosDoPeriodo dados,
            Map<Long, Evento> eventos,
            Map<Long, Funcao> funcoes,
            Map<Long, UsuarioResumo> pessoas,
            Map<Long, List<Violacao>> avisos) {
        var alertas = new ArrayList<Alerta>();
        for (Vaga vaga : dados.vagas()) {
            var violacoes = avisos.get(vaga.getId());
            var evento = eventos.get(vaga.getEventoId());
            if (violacoes == null || vaga.isForcada() || evento == null || !dados.porVir(evento)) {
                continue;
            }
            alertas.add(new Alerta(
                    evento.getInicio(),
                    new AlertaDaEscala(
                            funcoes.get(vaga.getFuncaoId()).getNome() + ", " + quando(evento.getInicio()) + " · "
                                    + evento.getNome() + " — " + nome(pessoas.get(vaga.getUsuarioId()))
                                    + " fora da regra",
                            violacoes.stream().map(Violacao::porQue).collect(Collectors.joining(" "))
                                    + " Troque a pessoa ou force a vaga com uma justificativa.",
                            (violacoes.size() == 1 ? "Regra: " : "Regras: ") + regras(violacoes))));
        }
        return alertas;
    }

    /** "LIMITE_POR_PERIODO · DISPONIBILIDADE". */
    private static String regras(List<Violacao> violacoes) {
        return violacoes.stream().map(violacao -> violacao.regra().name()).collect(Collectors.joining(" · "));
    }

    /**
     * Cada desistência que ainda espera alguém no lugar, nos eventos por vir: quem, função, evento e desde quando, com a
     * ação de preencher pelo ajuste da vaga. Some quando alguém entra na vaga.
     */
    private static List<Alerta> desistencias(
            DadosDoPeriodo dados,
            Map<Long, Evento> eventos,
            Map<Long, Funcao> funcoes,
            Map<Long, UsuarioResumo> pessoas) {
        var alertas = new ArrayList<Alerta>();
        for (Vaga vaga : dados.vagas()) {
            var evento = eventos.get(vaga.getEventoId());
            var funcao = funcoes.get(vaga.getFuncaoId());
            if (!vaga.isDesistida()
                    || evento == null
                    || !dados.porVir(evento)
                    || !MontagemDaEscala.vigente(vaga, evento, funcao)) {
                continue;
            }
            var desde = LocalDateTime.ofInstant(vaga.getDesistiuEm(), Fuso.SAO_PAULO);
            alertas.add(new Alerta(
                    evento.getInicio(),
                    new AlertaDaEscala(
                            nome(pessoas.get(vaga.getDesistenteId())) + " desistiu de " + funcao.getNome() + ", "
                                    + quando(evento.getInicio()) + " · " + evento.getNome(),
                            "A vaga está vazia desde " + Datas.diaEMes(desde.toLocalDate()) + " às "
                                    + Datas.horario(desde.toLocalTime())
                                    + ". Escolha quem entra no lugar: o ajuste confere as regras.",
                            null,
                            "arrow-left-right",
                            vaga.getId())));
        }
        return alertas;
    }

    /**
     * As vagas obrigatórias vazias dos eventos por vir, com o motivo (DiagnosticoDaVaga). A vaga de uma desistência
     * já tem o alerta dela.
     */
    private static List<Alerta> vagasVazias(DadosDoPeriodo dados) {
        var escala = MontagemDaEscala.montar(dados, false);
        var diagnostico = new DiagnosticoDaVaga(escala, dados.regras(), dados.nomesDosNiveis());
        var desistidas = dados.vagas().stream()
                .filter(Vaga::isDesistida)
                .map(Vaga::getId)
                .collect(Collectors.toSet());
        return escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() == null && vaga.isObrigatoria() && !desistidas.contains(vaga.getId()))
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
