package br.igreja.escala.escala.solver;

import static org.assertj.core.api.Assertions.assertThat;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * O solver de verdade no cenário da Mídia do seed: 19 pessoas que servem (a Natália não tem habilitação), dois cultos
 * por domingo e as quintas, a conferência de sábado no mês seguinte, limite de 3 por mês e pelo menos um Experiente por
 * evento. As respostas são as do SeedDeDesenvolvimento: na ordem do nome, as 12 primeiras respondem tudo (uma a cada
 * três vezes "Não pode"), as 3 seguintes respondem a metade e o resto não responde; a Ana só responde os três primeiros
 * eventos do mês seguinte. Termina quando a pontuação para de melhorar por 1 s.
 */
class GeracaoDaMidiaTest {

    private static final Long PROJECAO = 100L;
    private static final Long TRANSMISSAO = 101L;
    private static final Long INICIANTE = 200L;
    private static final Long EXPERIENTE = 201L;

    /** Outubro é o mês travado do seed; novembro, o seguinte, com a conferência e as respostas da Ana. */
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    /** Nome e nível em Projeção e Transmissão, como no SeedDeDesenvolvimento (nulo = sem habilitação). */
    private static final List<String[]> MEMBROS = List.of(
            new String[] {"Paula Ribeiro", "E", "E"},
            new String[] {"Ana Souza", "E", "E"},
            new String[] {"Lucas Lima", "I", null},
            new String[] {"Carla Dias", "I", "E"},
            new String[] {"Bruno Alves", null, "E"},
            new String[] {"Beatriz Rocha", "E", "I"},
            new String[] {"Diego Martins", "I", "I"},
            new String[] {"Elisa Costa", "E", null},
            new String[] {"Felipe Nunes", null, "I"},
            new String[] {"Gabriela Melo", "E", "E"},
            new String[] {"Henrique Prado", "I", null},
            new String[] {"Isabela Freitas", null, "E"},
            new String[] {"João Pedro Santos", "E", "I"},
            new String[] {"Larissa Teixeira", "I", "I"},
            new String[] {"Marcos Vieira", "E", null},
            new String[] {"Otávio Barros", "I", "E"},
            new String[] {"Priscila Cardoso", "E", "I"},
            new String[] {"Rafael Moreira", null, "I"},
            new String[] {"Sofia Araújo", "I", null});

    private static final RegrasDoMinisterio REGRAS = RegrasDoMinisterio.de(List.of(new RegraVigente(
            TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(EXPERIENTE, 1))));

    @ParameterizedTest
    @ValueSource(ints = {10, 11})
    void comAsRespostasDoSeedPreencheTodasAsVagasSemViolarRegraRigida(int mes) {
        var doMes = YearMonth.of(2026, mes);
        var resolvida = resolver(problema(doMes, Set.of()), EnvironmentMode.NO_ASSERT, Duration.ofSeconds(1));

        assertThat(resolvida.getPontuacao().hardScore()).isZero();
        assertThat(resolvida.getPontuacao().mediumScore())
                .as("nenhuma vaga vazia")
                .isZero();
        assertThat(resolvida.getVagas())
                .hasSize(2 * eventosDe(doMes).size())
                .allMatch(vaga -> vaga.getPessoa() != null);
        assertThat(cargas(resolvida).values()).allMatch(carga -> carga <= 3);
        assertThat(resolvida.getVagas().stream().collect(Collectors.groupingBy(VagaPlanejada::getEvento)))
                .as("todo evento tem um Experiente")
                .allSatisfy(
                        (evento, vagas) -> assertThat(vagas).anyMatch(vaga -> EXPERIENTE.equals(vaga.getNivelId())));
    }

    @Test
    void semNinguemDisponivelAVagaFicaVaziaEODiagnosticoDizPorque() {
        var quinta15 = eventosDe(OUTUBRO).stream()
                .filter(evento -> evento.inicio().getDayOfMonth() == 15)
                .findFirst()
                .orElseThrow();

        var resolvida =
                resolver(problema(OUTUBRO, Set.of(quinta15.id())), EnvironmentMode.NO_ASSERT, Duration.ofSeconds(1));

        assertThat(resolvida.getPontuacao().hardScore()).isZero();
        var vazias = resolvida.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() == null)
                .toList();
        assertThat(vazias).singleElement().satisfies(vaga -> {
            assertThat(vaga.getEvento()).isEqualTo(quinta15);
            assertThat(vaga.getFuncao().id()).isEqualTo(TRANSMISSAO);
        });
        assertThat(new DiagnosticoDaVaga(resolvida, REGRAS, Map.of(INICIANTE, "Iniciante", EXPERIENTE, "Experiente"))
                        .motivo(vazias.getFirst()))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.DISPONIBILIDADE, "Ninguém habilitado em Transmissão marcou Pode."));
    }

    /** Com FULL_ASSERT o Timefold recalcula a pontuação do zero a cada passo e falha se a incremental divergir. */
    @ParameterizedTest
    @ValueSource(ints = {10, 11})
    void asRestricoesNaoCorrompemAPontuacaoIncremental(int mes) {
        var resolvida = resolver(
                problema(YearMonth.of(2026, mes), Set.of()), EnvironmentMode.FULL_ASSERT, Duration.ofMillis(300));

        assertThat(resolvida.getPontuacao()).isNotNull();
    }

    private EscalaDoPeriodo resolver(EscalaDoPeriodo problema, EnvironmentMode modo, Duration semMelhorar) {
        var configuracao = new SolverConfig()
                .withSolutionClass(EscalaDoPeriodo.class)
                .withEntityClasses(VagaPlanejada.class)
                .withConstraintProviderClass(RestricoesDaEscala.class)
                .withEnvironmentMode(modo)
                .withRandomSeed(7L)
                .withTerminationConfig(new TerminationConfig()
                        .withUnimprovedSpentLimit(semMelhorar)
                        .withSpentLimit(Duration.ofSeconds(10)));
        return SolverFactory.<EscalaDoPeriodo>create(configuracao).buildSolver().solve(problema);
    }

    /** As respostas do seed no mês. Nos eventos de {@code semTransmissao}, ninguém habilitado em Transmissão pode. */
    private EscalaDoPeriodo problema(YearMonth mes, Set<Long> semTransmissao) {
        var eventos = eventosDe(mes);
        var emOrdemDeNome = MEMBROS.stream()
                .sorted(Comparator.comparing((String[] membro) -> membro[0]))
                .toList();
        var pessoas = new ArrayList<Pessoa>();
        for (int i = 0; i < emOrdemDeNome.size(); i++) {
            var membro = emOrdemDeNome.get(i);
            Map<Long, Long> niveis = new HashMap<>();
            if (membro[1] != null) {
                niveis.put(PROJECAO, nivel(membro[1]));
            }
            if (membro[2] != null) {
                niveis.put(TRANSMISSAO, nivel(membro[2]));
            }
            Set<Long> pode = new HashSet<>();
            int respondidos = i < 12 ? eventos.size() : (i < 15 ? eventos.size() / 2 : 0);
            for (int j = 0; j < respondidos && !membro[0].equals("Ana Souza"); j++) {
                if ((i + j) % 3 != 0) {
                    pode.add(eventos.get(j).id());
                }
            }
            if (membro[0].equals("Ana Souza") && !mes.equals(OUTUBRO)) {
                pode.add(eventos.get(0).id());
                pode.add(eventos.get(2).id());
            }
            if (niveis.containsKey(TRANSMISSAO)) {
                pode.removeAll(semTransmissao);
            }
            pessoas.add(new Pessoa(30L + i, membro[0], niveis, pode));
        }
        var projecao = new FuncaoDaEscala(PROJECAO, "Projeção", 1, 1);
        var transmissao = new FuncaoDaEscala(TRANSMISSAO, "Transmissão", 1, 1);
        var vagas = new ArrayList<VagaPlanejada>();
        long id = 1;
        for (EventoDaEscala evento : eventos) {
            vagas.add(new VagaPlanejada(id++, evento, projecao, 1, false, null));
            vagas.add(new VagaPlanejada(id++, evento, transmissao, 1, false, null));
        }
        return new EscalaDoPeriodo(
                400L, pessoas, List.of(), ParametrosDaEscala.de(REGRAS), vagas, PesosDasRegras.de(REGRAS));
    }

    private static Long nivel(String sigla) {
        return sigla.equals("I") ? INICIANTE : EXPERIENTE;
    }

    /**
     * Os eventos do mês em ordem, como os modelos do seed geram: domingos (manhã e noite) e quintas. O mês seguinte ao
     * travado tem também a conferência do terceiro sábado.
     */
    private static List<EventoDaEscala> eventosDe(YearMonth mes) {
        var eventos = new ArrayList<EventoDaEscala>();
        long id = 500L + 100L * mes.getMonthValue();
        for (var dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
                eventos.add(evento(id++, "Culto da manhã", dia.atTime(9, 30), 90));
                eventos.add(evento(id++, "Culto de domingo", dia.atTime(18, 0), 120));
            } else if (dia.getDayOfWeek() == DayOfWeek.THURSDAY) {
                eventos.add(evento(id++, "Culto de quinta", dia.atTime(19, 30), 120));
            } else if (!mes.equals(OUTUBRO)
                    && dia.equals(mes.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SATURDAY)))) {
                eventos.add(evento(id++, "Conferência de jovens", dia.atTime(15, 0), 240));
            }
        }
        return eventos;
    }

    private static EventoDaEscala evento(long id, String nome, LocalDateTime inicio, int minutos) {
        return new EventoDaEscala(id, nome, inicio, inicio.plusMinutes(minutos));
    }

    private static Map<Long, Long> cargas(EscalaDoPeriodo escala) {
        return escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() != null)
                .collect(Collectors.groupingBy(vaga -> vaga.getPessoa().id(), Collectors.counting()));
    }
}
