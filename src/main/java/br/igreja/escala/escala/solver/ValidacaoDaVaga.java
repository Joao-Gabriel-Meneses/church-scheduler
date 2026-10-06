package br.igreja.escala.escala.solver;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolutionUpdatePolicy;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Valida uma alocação manual com as próprias restrições do solver (RestricoesDaEscala), sem reescrever regra: o
 * SolutionManager.update calcula a pontuação da escala com a pessoa na vaga e sem ela, e o veredito é o do solver. O
 * analyze, que diria a regra de cada penalidade, é da versão paga do Timefold; aqui cada regra é pesada sozinha
 * (PesosDasRegras.isolando), e a regra é violada se a pontuação dela piora com a pessoa.
 *
 * <p>O mínimo por nível conta o evento inteiro: se o evento já está abaixo do mínimo, mais um Iniciante não muda a
 * falta, então também vale o evento sozinho depois da mudança. As outras regras pioram sempre que a pessoa as viola.
 *
 * <p>Muda a pessoa das vagas da escala durante o cálculo e devolve como estava: uma instância por requisição.
 */
public final class ValidacaoDaVaga {

    /** Regras que olham o evento inteiro: a mudança é recusada se o evento continua violando depois dela. */
    private static final Set<TipoDeRegra> DO_EVENTO_INTEIRO = Set.of(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);

    private final SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager;
    private final EscalaDoPeriodo escala;
    private final Map<Long, String> nomesDosNiveis;
    private final List<TipoDeRegra> rigidas;
    private final List<TipoDeRegra> brandas;
    private final Map<TipoDeRegra, Long> semAPessoa = new EnumMap<>(TipoDeRegra.class);
    private VagaPlanejada vagaDoCalculo;

    public ValidacaoDaVaga(
            SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager,
            EscalaDoPeriodo escala,
            RegrasDoMinisterio regras,
            Map<Long, String> nomesDosNiveis) {
        this.solutionManager = solutionManager;
        this.escala = escala;
        this.nomesDosNiveis = nomesDosNiveis;
        this.rigidas = regras.todas().stream()
                .filter(regra -> regra.ativa() && regra.rigidez() == Rigidez.HARD)
                .map(regra -> regra.tipo())
                .toList();
        this.brandas = regras.todas().stream()
                .filter(regra -> regra.ativa() && regra.rigidez() != Rigidez.HARD)
                .map(regra -> regra.tipo())
                .toList();
    }

    /** As regras rígidas que a pessoa viola nesta vaga, na ordem do catálogo; vazia se pode. */
    public List<Violacao> aoEscalar(VagaPlanejada vaga, Pessoa pessoa) {
        Objects.requireNonNull(pessoa, "pessoa");
        var original = vaga.getPessoa();
        try {
            var violadas = new ArrayList<Violacao>();
            for (TipoDeRegra tipo : rigidas) {
                long sem = semAPessoa(vaga, tipo);
                vaga.setPessoa(pessoa);
                long com = pontuar(escala, isolando(tipo)).hardScore();
                boolean viola = com < sem
                        || (DO_EVENTO_INTEIRO.contains(tipo)
                                && pontuar(doEvento(vaga.getEvento()), isolando(tipo))
                                                .hardScore()
                                        < 0);
                if (viola) {
                    violadas.add(new Violacao(tipo, porQue(tipo, vaga, pessoa)));
                }
            }
            return violadas;
        } finally {
            vaga.setPessoa(original);
        }
    }

    /** As regras rígidas que pioram se a vaga ficar vazia (ex.: tirar o único Experiente do evento). */
    public List<Violacao> aoEsvaziar(VagaPlanejada vaga) {
        var original = vaga.getPessoa();
        if (original == null) {
            return List.of();
        }
        try {
            var violadas = new ArrayList<Violacao>();
            for (TipoDeRegra tipo : rigidas) {
                long com = pontuar(escala, isolando(tipo)).hardScore();
                vaga.setPessoa(null);
                long sem = pontuar(escala, isolando(tipo)).hardScore();
                vaga.setPessoa(original);
                if (sem < com) {
                    violadas.add(new Violacao(tipo, porQueAoEsvaziar(tipo, vaga)));
                }
            }
            return violadas;
        } finally {
            vaga.setPessoa(original);
        }
    }

    /**
     * Por vaga preenchida, as regras rígidas que a pessoa dela viola como a escala está (a forçada, ou a vaga que outra
     * mudança afetou). Com a pontuação rígida zerada, não há aviso e basta um cálculo.
     */
    public Map<Long, List<Violacao>> avisos() {
        if (pontuar(escala, escala.getPesos()).hardScore() >= 0) {
            return Map.of();
        }
        Map<Long, List<Violacao>> avisos = new LinkedHashMap<>();
        for (TipoDeRegra tipo : rigidas) {
            var pesos = isolando(tipo);
            if (pontuar(escala, pesos).hardScore() >= 0) {
                continue;
            }
            for (VagaPlanejada vaga : preenchidas().toList()) {
                if (envolvida(vaga, tipo, pesos)) {
                    avisos.computeIfAbsent(vaga.getId(), id -> new ArrayList<>())
                            .add(new Violacao(tipo, porQue(tipo, vaga, vaga.getPessoa())));
                }
            }
        }
        return avisos;
    }

    /**
     * Quanto cada regra de prioridade e de preferência ligada pesa na escala como está, da maior para a menor, só as que
     * pesam (para o resumo antes de publicar).
     */
    public Map<TipoDeRegra, Long> penalidadesBrandas() {
        Map<TipoDeRegra, Long> penalidades = new EnumMap<>(TipoDeRegra.class);
        for (TipoDeRegra tipo : brandas) {
            long penalidade = -pontuar(escala, PesosDasRegras.isolando(tipo, HardMediumSoftScore.ONE_SOFT))
                    .softScore();
            if (penalidade > 0) {
                penalidades.put(tipo, penalidade);
            }
        }
        Map<TipoDeRegra, Long> ordenadas = new LinkedHashMap<>();
        penalidades.entrySet().stream()
                .sorted(Map.Entry.<TipoDeRegra, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(entrada -> ordenadas.put(entrada.getKey(), entrada.getValue()));
        return ordenadas;
    }

    /** A pessoa da vaga está entre as que violam a regra: sem ela, a regra pesa menos (ou o evento todo viola). */
    private boolean envolvida(
            VagaPlanejada vaga, TipoDeRegra tipo, ConstraintWeightOverrides<HardMediumSoftScore> pesos) {
        if (DO_EVENTO_INTEIRO.contains(tipo)) {
            return pontuar(doEvento(vaga.getEvento()), pesos).hardScore() < 0;
        }
        var pessoa = vaga.getPessoa();
        long com = pontuar(escala, pesos).hardScore();
        try {
            vaga.setPessoa(null);
            return pontuar(escala, pesos).hardScore() > com;
        } finally {
            vaga.setPessoa(pessoa);
        }
    }

    /** A pontuação da regra com a vaga vazia, guardada enquanto se testam os candidatos da mesma vaga. */
    private long semAPessoa(VagaPlanejada vaga, TipoDeRegra tipo) {
        if (vaga != vagaDoCalculo) {
            semAPessoa.clear();
            vagaDoCalculo = vaga;
        }
        return semAPessoa.computeIfAbsent(tipo, regra -> {
            vaga.setPessoa(null);
            return pontuar(escala, isolando(regra)).hardScore();
        });
    }

    private HardMediumSoftScore pontuar(EscalaDoPeriodo solucao, ConstraintWeightOverrides<HardMediumSoftScore> pesos) {
        var antes = solucao.getPesos();
        solucao.setPesos(pesos);
        try {
            return solutionManager.update(solucao, SolutionUpdatePolicy.UPDATE_SCORE_ONLY);
        } finally {
            solucao.setPesos(antes);
        }
    }

    private static ConstraintWeightOverrides<HardMediumSoftScore> isolando(TipoDeRegra tipo) {
        return PesosDasRegras.isolando(tipo, HardMediumSoftScore.ONE_HARD);
    }

    /** O evento sozinho: as vagas dele (as mesmas instâncias), sem os outros eventos nem os compromissos. */
    private EscalaDoPeriodo doEvento(EventoDaEscala evento) {
        return new EscalaDoPeriodo(
                escala.getPeriodoId(),
                escala.getPessoas(),
                List.of(),
                escala.getParametros(),
                escala.getVagas().stream()
                        .filter(vaga -> vaga.getEvento().equals(evento))
                        .toList(),
                escala.getPesos());
    }

    private Stream<VagaPlanejada> preenchidas() {
        return escala.getVagas().stream().filter(vaga -> vaga.getPessoa() != null);
    }

    /** O porquê para o gerente, com os números da escala. Só explica: quem decide que viola é o solver. */
    private String porQue(TipoDeRegra tipo, VagaPlanejada vaga, Pessoa pessoa) {
        var parametros = escala.getParametros();
        return switch (tipo) {
            case HABILITACAO -> "Não é habilitada em " + vaga.getFuncao().nome() + ".";
            case DISPONIBILIDADE -> "Não marcou Pode neste evento.";
            case UMA_FUNCAO_POR_EVENTO ->
                outrasDaPessoa(vaga, pessoa)
                        .filter(outra -> outra.getEvento().equals(vaga.getEvento()))
                        .findFirst()
                        .map(outra -> "Já serve em " + outra.getFuncao().nome() + " neste evento.")
                        .orElse("Já serve em outra função neste evento.");
            case SEM_SOBREPOSICAO -> "Já serve em outro evento nesse horário.";
            case LIMITE_POR_PERIODO -> {
                long eventos = eventosNoMes(vaga, pessoa);
                yield "Já tem " + eventos + (eventos == 1 ? " escala" : " escalas") + " no mês, e o limite é "
                        + parametros.limitePorMes() + ".";
            }
            case MIN_POR_NIVEL_NO_EVENTO -> {
                String nivel = nomesDosNiveis.get(parametros.nivelExigido());
                String dela = nomesDosNiveis.get(pessoa.nivelEm(vaga.getFuncao().id()));
                yield "O evento precisa de pelo menos " + parametros.minimoDoNivel()
                        + (parametros.minimoDoNivel() == 1 ? " pessoa" : " pessoas") + " do nível " + nivel
                        + (dela == null || dela.equals(nivel)
                                ? ", e ainda não tem."
                                : ", e " + pessoa.nome() + " é " + dela + ".");
            }
            case PESSOAS_POR_FUNCAO -> vaga.getFuncao().nome() + " já tem o máximo de pessoas neste evento.";
            default -> tipo.descricao();
        };
    }

    private String porQueAoEsvaziar(TipoDeRegra tipo, VagaPlanejada vaga) {
        if (tipo == TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO) {
            var parametros = escala.getParametros();
            return "Sem esta pessoa, o evento fica com menos de " + parametros.minimoDoNivel() + " do nível "
                    + nomesDosNiveis.get(parametros.nivelExigido())
                    + ". Troque por outra pessoa desse nível ou esvazie antes as outras vagas do evento.";
        }
        return tipo.descricao();
    }

    private Stream<VagaPlanejada> outrasDaPessoa(VagaPlanejada vaga, Pessoa pessoa) {
        return escala.getVagas().stream().filter(outra -> outra != vaga && pessoa.equals(outra.getPessoa()));
    }

    /** Em quantos eventos do mês a pessoa já está, sem contar esta vaga (vagas e compromissos que contam). */
    private long eventosNoMes(VagaPlanejada vaga, Pessoa pessoa) {
        var nasVagas =
                outrasDaPessoa(vaga, pessoa).map(outra -> outra.getEvento().id());
        var nosCompromissos = escala.getCompromissos().stream()
                .filter(compromisso ->
                        compromisso.contaNoPeriodo() && compromisso.pessoaId().equals(pessoa.id()))
                .map(CompromissoFixo::eventoId);
        return Stream.concat(nasVagas, nosCompromissos)
                .filter(evento -> !evento.equals(vaga.getEvento().id()))
                .distinct()
                .count();
    }

    /** Uma regra violada e o porquê. Forçável: o gerente pode escalar assim mesmo, com justificativa. */
    public record Violacao(TipoDeRegra regra, String porQue) {

        public boolean forcavel() {
            return regra.isForcavel();
        }
    }
}
