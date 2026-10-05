package br.igreja.escala.escala.solver;

import static br.igreja.escala.escala.solver.Cenario.EXPERIENTE;
import static br.igreja.escala.escala.solver.Cenario.INICIANTE;
import static br.igreja.escala.escala.solver.Cenario.PROJECAO;
import static br.igreja.escala.escala.solver.Cenario.TRANSMISSAO;
import static br.igreja.escala.escala.solver.Cenario.culto;
import static br.igreja.escala.escala.solver.Cenario.evento;
import static br.igreja.escala.escala.solver.Cenario.pessoa;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A validação do ajuste manual usa as restrições do solver: para cada regra rígida, um cenário em que a validação
 * aponta a regra e o solver, com só aquela pessoa para a vaga, a deixa vazia (o mesmo veredito). E o caso em que a
 * validação aceita e o solver escala a pessoa.
 */
class ValidacaoDaVagaTest {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoDaVagaTest.class);
    private static final Map<Long, String> NIVEIS = Map.of(INICIANTE, "Iniciante", EXPERIENTE, "Experiente");
    private static final FuncaoDaEscala SOM = new FuncaoDaEscala(102L, "Som", 1, 1);

    private static final SolverFactory<EscalaDoPeriodo> FABRICA = SolverFactory.create(new SolverConfig()
            .withSolutionClass(EscalaDoPeriodo.class)
            .withEntityClasses(VagaPlanejada.class)
            .withConstraintProviderClass(RestricoesDaEscala.class)
            .withRandomSeed(7L)
            .withTerminationConfig(new TerminationConfig()
                    .withUnimprovedSpentLimit(Duration.ofMillis(200))
                    .withSpentLimit(Duration.ofSeconds(5))));
    private static final SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> SOLUTION_MANAGER =
            SolutionManager.create(FABRICA);

    private final EventoDaEscala domingo = culto(1L, 4);

    private RegrasDoMinisterio regras = RegrasDoMinisterio.padrao();
    private final List<CompromissoFixo> compromissos = new ArrayList<>();

    @Nested
    class MesmoVereditoQueOSolver {

        @Test
        void habilitacao() {
            var soProjecao = pessoa(30, Map.of(PROJECAO.id(), EXPERIENTE), 1L);
            var vaga = solta(1, domingo, TRANSMISSAO);

            mesmoVeredito(vaga, soProjecao, List.of(vaga), TipoDeRegra.HABILITACAO);
        }

        @Test
        void disponibilidade() {
            var naoPode = pessoa(30);
            var vaga = solta(1, domingo, PROJECAO);

            mesmoVeredito(vaga, naoPode, List.of(vaga), TipoDeRegra.DISPONIBILIDADE);
        }

        @Test
        void umaFuncaoPorEvento() {
            var ana = pessoa(30, 1L);
            var vaga = solta(2, domingo, TRANSMISSAO);

            mesmoVeredito(
                    vaga, ana, List.of(presa(1, domingo, PROJECAO, ana), vaga), TipoDeRegra.UMA_FUNCAO_POR_EVENTO);
        }

        @Test
        void semSobreposicaoComOutraVaga() {
            var manha = evento(1L, 4, 9, 30, 90);
            var ensaio = evento(2L, 4, 10, 0, 60);
            var ana = pessoa(30, 1L, 2L);
            var vaga = solta(2, ensaio, PROJECAO);

            mesmoVeredito(vaga, ana, List.of(presa(1, manha, PROJECAO, ana), vaga), TipoDeRegra.SEM_SOBREPOSICAO);
        }

        @Test
        void semSobreposicaoComCompromissoDeOutroMinisterio() {
            var ana = pessoa(30, 1L);
            compromissos.add(new CompromissoFixo(30L, 900L, domingo.inicio(), domingo.fim(), false));
            var vaga = solta(1, domingo, PROJECAO);

            mesmoVeredito(vaga, ana, List.of(vaga), TipoDeRegra.SEM_SOBREPOSICAO);
        }

        @Test
        void limitePorPeriodo() {
            regras = comRegras(limite(1));
            var quinta = culto(2L, 8);
            var ana = pessoa(30, 1L, 2L);
            var vaga = solta(2, quinta, PROJECAO);

            mesmoVeredito(vaga, ana, List.of(presa(1, domingo, PROJECAO, ana), vaga), TipoDeRegra.LIMITE_POR_PERIODO);
        }

        @Test
        void minimoPorNivelNoEvento() {
            regras = comRegras(minimoDeExperientes());
            var lucas = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
            var vaga = solta(1, domingo, PROJECAO);

            mesmoVeredito(
                    vaga, lucas, List.of(vaga, solta(2, domingo, TRANSMISSAO)), TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
        }

        @Test
        void quemPassaEmTudoOSolverEscala() {
            var ana = pessoa(30, 1L);
            var vaga = solta(1, domingo, PROJECAO);
            var escala = escala(List.of(ana), List.of(vaga));

            assertThat(validacao(escala).aoEscalar(vaga, ana)).isEmpty();
            var resolvida = FABRICA.buildSolver().solve(escala);
            assertThat(resolvida.getPontuacao().hardScore()).isZero();
            assertThat(resolvida.getVagas().getFirst().getPessoa()).isEqualTo(ana);
        }

        /** A validação aponta a regra, e o solver, só com essa pessoa, deixa a vaga vazia sem violar nada. */
        private void mesmoVeredito(VagaPlanejada vaga, Pessoa pessoa, List<VagaPlanejada> vagas, TipoDeRegra regra) {
            var escala = escala(List.of(pessoa), vagas);

            assertThat(validacao(escala).aoEscalar(vaga, pessoa))
                    .extracting(Violacao::regra)
                    .containsExactly(regra);
            assertThat(vaga.getPessoa())
                    .as("a validação devolve a vaga como estava")
                    .isNull();

            var resolvida = FABRICA.buildSolver().solve(escala);
            assertThat(resolvida.getPontuacao().hardScore()).isZero();
            assertThat(resolvida.getVagas())
                    .filteredOn(planejada -> planejada.getId().equals(vaga.getId()))
                    .singleElement()
                    .satisfies(planejada -> assertThat(planejada.getPessoa()).isNull());
        }
    }

    @Nested
    class MinimoPorNivel {

        private final Pessoa lucas = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
        private final Pessoa diego = pessoa(31, Map.of(TRANSMISSAO.id(), INICIANTE), 1L);
        private final Pessoa felipe = pessoa(32, Map.of(SOM.id(), INICIANTE), 1L);
        private final Pessoa ana = pessoa(33, Map.of(SOM.id(), EXPERIENTE), 1L);

        @Test
        void oTerceiroInicianteERecusadoMesmoSemMudarAFalta() {
            regras = comRegras(minimoDeExperientes());
            var vaga = solta(3, domingo, SOM);
            var escala = escala(
                    List.of(lucas, diego, felipe),
                    List.of(presa(1, domingo, PROJECAO, lucas), presa(2, domingo, TRANSMISSAO, diego), vaga));

            assertThat(validacao(escala).aoEscalar(vaga, felipe))
                    .singleElement()
                    .satisfies(violacao -> {
                        assertThat(violacao.regra()).isEqualTo(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
                        assertThat(violacao.forcavel()).isFalse();
                        assertThat(violacao.porQue())
                                .isEqualTo("O evento precisa de pelo menos 1 pessoa do nível Experiente, e Pessoa 32 é"
                                        + " Iniciante.");
                    });
        }

        @Test
        void doisIniciantesEUmExperienteSaoAceitos() {
            regras = comRegras(minimoDeExperientes());
            var vaga = solta(3, domingo, SOM);
            var escala = escala(
                    List.of(lucas, diego, ana),
                    List.of(presa(1, domingo, PROJECAO, lucas), presa(2, domingo, TRANSMISSAO, diego), vaga));

            assertThat(validacao(escala).aoEscalar(vaga, ana)).isEmpty();
        }

        @Test
        void tirarOUnicoExperienteComIniciantesNoEventoERecusado() {
            regras = comRegras(minimoDeExperientes());
            var vagaDaAna = presa(3, domingo, SOM, ana);
            var escala = escala(List.of(lucas, ana), List.of(presa(1, domingo, PROJECAO, lucas), vagaDaAna));

            assertThat(validacao(escala).aoEsvaziar(vagaDaAna))
                    .extracting(Violacao::regra)
                    .containsExactly(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
            assertThat(vagaDaAna.getPessoa()).isEqualTo(ana);
        }

        @Test
        void tirarOInicianteNaoPiora() {
            regras = comRegras(minimoDeExperientes());
            var vagaDoLucas = presa(1, domingo, PROJECAO, lucas);
            var escala = escala(List.of(lucas, ana), List.of(vagaDoLucas, presa(3, domingo, SOM, ana)));

            assertThat(validacao(escala).aoEsvaziar(vagaDoLucas)).isEmpty();
        }
    }

    @Test
    void regraDesligadaNaoEApontada() {
        regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.LIMITE_POR_PERIODO, Rigidez.HARD, 1, false, new LimitePorPeriodoParams(1))));
        var ana = pessoa(30, 1L, 2L);
        var vaga = solta(2, culto(2L, 8), PROJECAO);
        var escala = escala(List.of(ana), List.of(presa(1, domingo, PROJECAO, ana), vaga));

        assertThat(validacao(escala).aoEscalar(vaga, ana)).isEmpty();
    }

    @Test
    void violacaoQueJaExistiaNaoPesaNaCandidata() {
        regras = comRegras(limite(1));
        var quinta = culto(2L, 8);
        var sabado = culto(3L, 10);
        var bruno = pessoa(31, 1L, 2L, 3L);
        var carla = pessoa(32, 3L);
        var vaga = solta(3, sabado, PROJECAO);
        var escala = escala(
                List.of(bruno, carla),
                List.of(presa(1, domingo, PROJECAO, bruno), presa(2, quinta, TRANSMISSAO, bruno), vaga));

        assertThat(validacao(escala).aoEscalar(vaga, carla))
                .as("o limite estourado do Bruno")
                .isEmpty();
        assertThat(validacao(escala).aoEscalar(vaga, bruno))
                .extracting(Violacao::regra, Violacao::porQue)
                .containsExactly(tuple(TipoDeRegra.LIMITE_POR_PERIODO, "Já tem 2 escalas no mês, e o limite é 1."));
    }

    @Test
    void umaFuncaoPorEventoDizQualFuncao() {
        var ana = pessoa(30, 1L);
        var vaga = solta(2, domingo, TRANSMISSAO);
        var escala = escala(List.of(ana), List.of(presa(1, domingo, PROJECAO, ana), vaga));

        assertThat(validacao(escala).aoEscalar(vaga, ana))
                .extracting(Violacao::porQue)
                .containsExactly("Já serve em Projeção neste evento.");
    }

    @Test
    void forcaveisSaoSoOLimiteEADisponibilidade() {
        assertThat(Arrays.stream(TipoDeRegra.values()).filter(TipoDeRegra::isForcavel))
                .containsExactly(TipoDeRegra.DISPONIBILIDADE, TipoDeRegra.LIMITE_POR_PERIODO);
    }

    @Test
    void avisosApontamAsVagasQueViolamComoAEscalaEsta() {
        regras = comRegras(minimoDeExperientes());
        var lucas = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
        var diego = pessoa(31, Map.of(TRANSMISSAO.id(), INICIANTE));
        var ana = pessoa(32, 2L);
        var quinta = culto(2L, 8);
        var escala = escala(
                List.of(lucas, diego, ana),
                List.of(
                        presa(1, domingo, PROJECAO, lucas),
                        presa(2, domingo, TRANSMISSAO, diego),
                        presa(3, quinta, PROJECAO, ana)));

        var avisos = validacao(escala).avisos();

        assertThat(avisos.get(1L)).extracting(Violacao::regra).containsExactly(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
        assertThat(avisos.get(2L))
                .extracting(Violacao::regra)
                .containsExactly(TipoDeRegra.DISPONIBILIDADE, TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
        assertThat(avisos).doesNotContainKey(3L);
    }

    @Test
    void semViolacaoNaoHaAvisoEAsBrandasDizemQuantoPesam() {
        var ana = pessoa(30, 1L);
        var escala = escala(List.of(ana), List.of(presa(1, domingo, PROJECAO, ana), solta(2, domingo, TRANSMISSAO)));
        var validacao = validacao(escala);

        assertThat(validacao.avisos()).isEmpty();
        assertThat(validacao.penalidadesBrandas())
                .containsExactly(
                        Map.entry(TipoDeRegra.PRIORIDADE_POR_DATA, 100L + 27),
                        Map.entry(TipoDeRegra.EQUILIBRIO_DE_CARGA, 1L));
    }

    /** O cenário da Mídia: 19 pessoas e 28 vagas. Abrir os candidatos de uma vaga tem de caber em menos de 2 s. */
    @Test
    void candidatosDeUmaVagaNoTamanhoDaMidia() {
        regras = comRegras(limite(3), minimoDeExperientes());
        var pessoas = new ArrayList<Pessoa>();
        var eventos = new ArrayList<EventoDaEscala>();
        for (int dia = 1; dia <= 28; dia += 2) {
            eventos.add(culto(dia, dia));
        }
        for (int i = 0; i < 19; i++) {
            var pode = eventos.stream()
                    .map(EventoDaEscala::id)
                    .filter(id -> id % 3 != 0)
                    .toArray(Long[]::new);
            pessoas.add(pessoa(
                    30 + i,
                    Map.of(PROJECAO.id(), i % 2 == 0 ? EXPERIENTE : INICIANTE, TRANSMISSAO.id(), EXPERIENTE),
                    pode));
        }
        var vagas = new ArrayList<VagaPlanejada>();
        long id = 1;
        for (EventoDaEscala evento : eventos) {
            vagas.add(solta(id++, evento, PROJECAO));
            vagas.add(solta(id++, evento, TRANSMISSAO));
        }
        var escala = FABRICA.buildSolver().solve(escala(pessoas, vagas));
        var validacao = validacao(escala);
        var vaga = escala.getVagas().get(4);

        long inicio = System.nanoTime();
        escala.getPessoas().forEach(pessoa -> validacao.aoEscalar(vaga, pessoa));
        validacao.avisos();
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        log.info("Candidatos de uma vaga (19 pessoas, 28 vagas) e avisos validados em {} ms", ms);
        assertThat(ms).isLessThan(2000);
    }

    private EscalaDoPeriodo escala(List<Pessoa> pessoas, List<VagaPlanejada> vagas) {
        return new EscalaDoPeriodo(
                400L,
                pessoas,
                compromissos,
                ParametrosDaEscala.de(regras),
                new ArrayList<>(vagas),
                PesosDasRegras.de(regras));
    }

    private ValidacaoDaVaga validacao(EscalaDoPeriodo escala) {
        return new ValidacaoDaVaga(SOLUTION_MANAGER, escala, regras, NIVEIS);
    }

    private static VagaPlanejada solta(long id, EventoDaEscala evento, FuncaoDaEscala funcao) {
        return new VagaPlanejada(id, evento, funcao, 1, false, null);
    }

    private static VagaPlanejada presa(long id, EventoDaEscala evento, FuncaoDaEscala funcao, Pessoa pessoa) {
        return new VagaPlanejada(id, evento, funcao, 1, true, pessoa);
    }

    private static RegraVigente limite(int maximo) {
        return new RegraVigente(
                TipoDeRegra.LIMITE_POR_PERIODO, Rigidez.HARD, 1, true, new LimitePorPeriodoParams(maximo));
    }

    private static RegraVigente minimoDeExperientes() {
        return new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(EXPERIENTE, 1));
    }

    private static RegrasDoMinisterio comRegras(RegraVigente... vigentes) {
        return RegrasDoMinisterio.de(List.of(vigentes));
    }
}
