package br.igreja.escala.escala.solver;

import static org.assertj.core.api.Assertions.assertThat;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import br.igreja.escala.escala.domain.MaxPorNivelParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * O solver de verdade no cenário da Mídia do seed: 19 pessoas que servem (a Natália não tem habilitação), outubro de
 * 2026 com dois cultos por domingo, as quintas e a conferência de sábado, limite de 3 por mês e no máximo um Iniciante
 * por evento. Termina quando a pontuação para de melhorar por 1 s.
 */
class GeracaoDaMidiaTest {

    private static final Long PROJECAO = 100L;
    private static final Long TRANSMISSAO = 101L;
    private static final Long INICIANTE = 200L;
    private static final Long EXPERIENTE = 201L;

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
            TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MaxPorNivelParams(INICIANTE, 1))));

    private final List<EventoDaEscala> eventos = eventosDeOutubro();

    @Test
    void comSolucaoPreencheTodasAsVagasSemViolarRegraRigida() {
        var resolvida = resolver(problema(Set.of()), EnvironmentMode.NO_ASSERT, Duration.ofSeconds(1));

        assertThat(resolvida.getPontuacao().hardScore()).isZero();
        assertThat(resolvida.getPontuacao().mediumScore())
                .as("nenhuma vaga vazia")
                .isZero();
        assertThat(resolvida.getVagas()).hasSize(28).allMatch(vaga -> vaga.getPessoa() != null);
        assertThat(cargas(resolvida).values()).allMatch(carga -> carga <= 3);
    }

    @Test
    void semNinguemDisponivelAVagaFicaVaziaEODiagnosticoDizPorque() {
        var quinta15 = eventos.stream()
                .filter(evento -> evento.inicio().getDayOfMonth() == 15)
                .findFirst()
                .orElseThrow();

        var resolvida = resolver(problema(Set.of(quinta15.id())), EnvironmentMode.NO_ASSERT, Duration.ofSeconds(1));

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
    @Test
    void asRestricoesNaoCorrompemAPontuacaoIncremental() {
        var resolvida = resolver(problema(Set.of()), EnvironmentMode.FULL_ASSERT, Duration.ofMillis(300));

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

    /**
     * Cada pessoa marca Pode em 2 de cada 3 eventos, como no seed. Nos eventos de {@code semTransmissao}, ninguém
     * habilitado em Transmissão pode.
     */
    private EscalaDoPeriodo problema(Set<Long> semTransmissao) {
        var pessoas = new ArrayList<Pessoa>();
        for (int i = 0; i < MEMBROS.size(); i++) {
            var membro = MEMBROS.get(i);
            Map<Long, Long> niveis = new HashMap<>();
            if (membro[1] != null) {
                niveis.put(PROJECAO, nivel(membro[1]));
            }
            if (membro[2] != null) {
                niveis.put(TRANSMISSAO, nivel(membro[2]));
            }
            Set<Long> pode = new HashSet<>();
            for (int j = 0; j < eventos.size(); j++) {
                var evento = eventos.get(j);
                boolean bloqueado = semTransmissao.contains(evento.id()) && niveis.containsKey(TRANSMISSAO);
                if ((i + j) % 3 != 0 && !bloqueado) {
                    pode.add(evento.id());
                }
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

    /** Domingos (manhã e noite), quintas e a conferência do terceiro sábado: 14 eventos, 28 vagas. */
    private static List<EventoDaEscala> eventosDeOutubro() {
        var eventos = new ArrayList<EventoDaEscala>();
        long id = 500;
        for (int domingo : new int[] {4, 11, 18, 25}) {
            eventos.add(evento(id++, "Culto da manhã", domingo, 9, 30, 90));
            eventos.add(evento(id++, "Culto de domingo", domingo, 18, 0, 120));
        }
        for (int quinta : new int[] {1, 8, 15, 22, 29}) {
            eventos.add(evento(id++, "Culto de quinta", quinta, 19, 30, 120));
        }
        eventos.add(evento(id, "Conferência de jovens", 17, 15, 0, 240));
        return eventos;
    }

    private static EventoDaEscala evento(long id, String nome, int dia, int hora, int minuto, int minutos) {
        var inicio = LocalDateTime.of(2026, 10, dia, hora, minuto);
        return new EventoDaEscala(id, nome, inicio, inicio.plusMinutes(minutos));
    }

    private static Map<Long, Long> cargas(EscalaDoPeriodo escala) {
        return escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() != null)
                .collect(Collectors.groupingBy(vaga -> vaga.getPessoa().id(), Collectors.counting()));
    }
}
