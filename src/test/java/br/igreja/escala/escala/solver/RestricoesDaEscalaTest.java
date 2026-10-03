package br.igreja.escala.escala.solver;

import static br.igreja.escala.escala.solver.Cenario.EXPERIENTE;
import static br.igreja.escala.escala.solver.Cenario.INICIANTE;
import static br.igreja.escala.escala.solver.Cenario.PROJECAO;
import static br.igreja.escala.escala.solver.Cenario.TRANSMISSAO;
import static br.igreja.escala.escala.solver.Cenario.culto;
import static br.igreja.escala.escala.solver.Cenario.evento;
import static br.igreja.escala.escala.solver.Cenario.limite;
import static br.igreja.escala.escala.solver.Cenario.maximoDeIniciantes;
import static br.igreja.escala.escala.solver.Cenario.pessoa;
import static br.igreja.escala.escala.solver.Cenario.vaga;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Cada restrição no ConstraintVerifier: o caso que penaliza e o que não penaliza. */
class RestricoesDaEscalaTest {

    private final ConstraintVerifier<RestricoesDaEscala, EscalaDoPeriodo> verificador =
            ConstraintVerifier.build(new RestricoesDaEscala(), EscalaDoPeriodo.class, VagaPlanejada.class);

    private final EventoDaEscala domingo = culto(1L, 4);
    private final EventoDaEscala quinta = culto(2L, 8);

    @Nested
    class PessoasPorFuncao {

        @Test
        void penalizaQuemPassaDoMaximoDaFuncao() {
            verificador
                    .verifyThat(RestricoesDaEscala::pessoasPorFuncao)
                    .given(
                            vaga(1, domingo, PROJECAO, 1, pessoa(30)),
                            vaga(2, domingo, PROJECAO, 2, pessoa(31)),
                            vaga(3, domingo, PROJECAO, 3, pessoa(32)))
                    .penalizesBy(2);
        }

        @Test
        void naoPenalizaAteOMaximoNemVagaVazia() {
            var vocal = new FuncaoDaEscala(102L, "Vocal", 1, 3);
            verificador
                    .verifyThat(RestricoesDaEscala::pessoasPorFuncao)
                    .given(
                            vaga(1, domingo, vocal, 1, pessoa(30)),
                            vaga(2, domingo, vocal, 2, pessoa(31)),
                            vaga(3, domingo, PROJECAO, 1, pessoa(32)),
                            vaga(4, domingo, PROJECAO, 2, null))
                    .penalizesBy(0);
        }
    }

    @Nested
    class Habilitacao {

        @Test
        void penalizaQuemNaoEHabilitadoNaFuncao() {
            var soProjecao = pessoa(30, Map.of(PROJECAO.id(), EXPERIENTE), 1L);
            verificador
                    .verifyThat(RestricoesDaEscala::habilitacao)
                    .given(vaga(1, domingo, TRANSMISSAO, soProjecao))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaQuemEHabilitado() {
            var soProjecao = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
            verificador
                    .verifyThat(RestricoesDaEscala::habilitacao)
                    .given(vaga(1, domingo, PROJECAO, soProjecao), vaga(2, domingo, TRANSMISSAO, null))
                    .penalizesBy(0);
        }
    }

    @Nested
    class Disponibilidade {

        @Test
        void penalizaQuemNaoMarcouPodeNoEvento() {
            verificador
                    .verifyThat(RestricoesDaEscala::disponibilidade)
                    .given(vaga(1, domingo, PROJECAO, pessoa(30, 2L)))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaQuemMarcouPode() {
            verificador
                    .verifyThat(RestricoesDaEscala::disponibilidade)
                    .given(vaga(1, domingo, PROJECAO, pessoa(30, 1L)), vaga(2, quinta, PROJECAO, null))
                    .penalizesBy(0);
        }
    }

    @Nested
    class UmaFuncaoPorEvento {

        @Test
        void penalizaAMesmaPessoaEmDuasFuncoesDoEvento() {
            var ana = pessoa(30, 1L);
            verificador
                    .verifyThat(RestricoesDaEscala::umaFuncaoPorEvento)
                    .given(vaga(1, domingo, PROJECAO, ana), vaga(2, domingo, TRANSMISSAO, ana))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaAMesmaPessoaEmEventosDiferentes() {
            var ana = pessoa(30, 1L, 2L);
            verificador
                    .verifyThat(RestricoesDaEscala::umaFuncaoPorEvento)
                    .given(vaga(1, domingo, PROJECAO, ana), vaga(2, quinta, TRANSMISSAO, ana))
                    .penalizesBy(0);
        }
    }

    @Nested
    class SemSobreposicao {

        private final EventoDaEscala cultoDas18 = evento(1L, 4, 18, 0, 120);
        private final EventoDaEscala ensaioDas19 = evento(2L, 4, 19, 0, 60);
        private final EventoDaEscala manhaAte11 = evento(3L, 4, 9, 30, 90);
        private final EventoDaEscala almocoDas11 = evento(4L, 4, 11, 0, 60);

        @Test
        void penalizaAMesmaPessoaEmEventosQueSeSobrepoem() {
            var ana = pessoa(30, 1L, 2L);
            verificador
                    .verifyThat(RestricoesDaEscala::semSobreposicao)
                    .given(vaga(1, cultoDas18, PROJECAO, ana), vaga(2, ensaioDas19, PROJECAO, ana))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaEventosColadosNemPessoasDiferentes() {
            var ana = pessoa(30, 1L, 2L, 3L, 4L);
            verificador
                    .verifyThat(RestricoesDaEscala::semSobreposicao)
                    .given(
                            vaga(1, manhaAte11, PROJECAO, ana),
                            vaga(2, almocoDas11, PROJECAO, ana),
                            vaga(3, cultoDas18, PROJECAO, ana),
                            vaga(4, ensaioDas19, PROJECAO, pessoa(31, 2L)))
                    .penalizesBy(0);
        }

        @Test
        void penalizaCompromissoDeOutroMinisterioNoMesmoHorario() {
            var louvor = new CompromissoFixo(30L, 900L, cultoDas18.inicio().plusMinutes(30), cultoDas18.fim(), false);
            verificador
                    .verifyThat(RestricoesDaEscala::semSobreposicao)
                    .given(vaga(1, cultoDas18, PROJECAO, pessoa(30, 1L)), louvor)
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaCompromissoColadoNemDeOutraPessoa() {
            var colado = new CompromissoFixo(
                    30L, 900L, cultoDas18.fim(), cultoDas18.fim().plusHours(1), false);
            var deOutra = new CompromissoFixo(31L, 901L, cultoDas18.inicio(), cultoDas18.fim(), false);
            verificador
                    .verifyThat(RestricoesDaEscala::semSobreposicao)
                    .given(vaga(1, cultoDas18, PROJECAO, pessoa(30, 1L)), colado, deOutra)
                    .penalizesBy(0);
        }
    }

    @Nested
    class LimitePorPeriodo {

        private final EventoDaEscala manha = evento(3L, 4, 9, 30, 90);
        private final EventoDaEscala sabado = culto(4L, 10);

        @Test
        void penalizaOQuePassaDoLimiteContandoDoisCultosNoMesmoDia() {
            var ana = pessoa(30, 1L, 2L, 3L, 4L);
            verificador
                    .verifyThat(RestricoesDaEscala::limitePorPeriodo)
                    .given(
                            limite(3),
                            vaga(1, manha, PROJECAO, ana),
                            vaga(2, domingo, PROJECAO, ana),
                            vaga(3, quinta, PROJECAO, ana),
                            vaga(4, sabado, PROJECAO, ana))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaAteOLimite() {
            var ana = pessoa(30, 1L, 2L, 3L);
            verificador
                    .verifyThat(RestricoesDaEscala::limitePorPeriodo)
                    .given(
                            limite(3),
                            vaga(1, manha, PROJECAO, ana),
                            vaga(2, domingo, PROJECAO, ana),
                            vaga(3, quinta, PROJECAO, ana),
                            vaga(4, sabado, PROJECAO, null))
                    .penalizesBy(0);
        }

        @Test
        void contaOsEventosDoMesQueJaComecaramMasNaoOsDeOutroMinisterio() {
            var ana = pessoa(30, 2L, 4L);
            var jaServiu = new CompromissoFixo(30L, 800L, manha.inicio(), manha.fim(), true);
            var noLouvor = new CompromissoFixo(
                    30L, 900L, manha.inicio().plusDays(1), manha.fim().plusDays(1), false);
            verificador
                    .verifyThat(RestricoesDaEscala::limitePorPeriodo)
                    .given(
                            limite(2),
                            jaServiu,
                            noLouvor,
                            vaga(1, quinta, PROJECAO, ana),
                            vaga(2, sabado, PROJECAO, ana))
                    .penalizesBy(1);
        }
    }

    @Nested
    class MaxPorNivelNoEvento {

        private final Pessoa lucas = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
        private final Pessoa diego = pessoa(31, Map.of(TRANSMISSAO.id(), INICIANTE), 1L);
        private final Pessoa ana = pessoa(32, Map.of(TRANSMISSAO.id(), EXPERIENTE), 1L);

        @Test
        void penalizaDoisIniciantesNoMesmoEvento() {
            verificador
                    .verifyThat(RestricoesDaEscala::maxPorNivelNoEvento)
                    .given(
                            maximoDeIniciantes(1),
                            vaga(1, domingo, PROJECAO, lucas),
                            vaga(2, domingo, TRANSMISSAO, diego))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaUmIniciantePorEventoNemComARegraDesligada() {
            verificador
                    .verifyThat(RestricoesDaEscala::maxPorNivelNoEvento)
                    .given(maximoDeIniciantes(1), vaga(1, domingo, PROJECAO, lucas), vaga(2, domingo, TRANSMISSAO, ana))
                    .penalizesBy(0);
            verificador
                    .verifyThat(RestricoesDaEscala::maxPorNivelNoEvento)
                    .given(limite(3), vaga(1, domingo, PROJECAO, lucas), vaga(2, domingo, TRANSMISSAO, diego))
                    .penalizesBy(0);
        }
    }

    @Nested
    class PrioridadePorData {

        @Test
        void vagaObrigatoriaVaziaPesaMaisPertoDoInicioDoMes() {
            var dia25 = culto(3L, 25);
            verificador
                    .verifyThat(RestricoesDaEscala::prioridadePorData)
                    .given(vaga(1, domingo, PROJECAO, null))
                    .penalizesBy(100 + 27);
            verificador
                    .verifyThat(RestricoesDaEscala::prioridadePorData)
                    .given(vaga(1, dia25, PROJECAO, null))
                    .penalizesBy(100 + 6);
        }

        @Test
        void vagaOpcionalVaziaPesaPouco() {
            var vocal = new FuncaoDaEscala(102L, "Vocal", 1, 3);
            verificador
                    .verifyThat(RestricoesDaEscala::prioridadePorData)
                    .given(vaga(1, domingo, vocal, 1, pessoa(30, 1L)), vaga(2, domingo, vocal, 2, null))
                    .penalizesBy(1);
        }

        @Test
        void naoPenalizaVagaPreenchida() {
            verificador
                    .verifyThat(RestricoesDaEscala::prioridadePorData)
                    .given(vaga(1, domingo, PROJECAO, pessoa(30, 1L)))
                    .penalizesBy(0);
        }
    }

    @Nested
    class EquilibrioDeCarga {

        @Test
        void penalizaOQuadradoDasEscalasDeCadaPessoa() {
            var ana = pessoa(30, 1L, 2L);
            var bruno = pessoa(31, 1L);
            verificador
                    .verifyThat(RestricoesDaEscala::equilibrioDeCarga)
                    .given(
                            vaga(1, domingo, PROJECAO, ana),
                            vaga(2, quinta, PROJECAO, ana),
                            vaga(3, domingo, TRANSMISSAO, bruno))
                    .penalizesBy(2 * 2 + 1);
        }

        @Test
        void naoPenalizaNinguemSemEscala() {
            verificador
                    .verifyThat(RestricoesDaEscala::equilibrioDeCarga)
                    .given(vaga(1, domingo, PROJECAO, null))
                    .penalizesBy(0);
        }
    }

    @Nested
    class PesosDoMinisterio {

        private final Pessoa ana = pessoa(30, 1L, 2L);

        @Test
        void regraLigadaPesaNaRigidezDela() {
            verificador
                    .verifyThat()
                    .givenSolution(escala(RegrasDoMinisterio.padrao(), 1))
                    .scores(HardMediumSoftScore.of(-1, 0, -4));
        }

        @Test
        void regraDesligadaNaoPesa() {
            var semLimite = RegrasDoMinisterio.de(List.of(new RegraVigente(
                    TipoDeRegra.LIMITE_POR_PERIODO, Rigidez.HARD, 1, false, new LimitePorPeriodoParams(1))));
            verificador.verifyThat().givenSolution(escala(semLimite, 1)).scores(HardMediumSoftScore.of(0, 0, -4));
        }

        /** Ana em dois eventos com limite 1: passa um do limite e pesa 2² no equilíbrio. */
        private EscalaDoPeriodo escala(RegrasDoMinisterio regras, int limitePorMes) {
            return new EscalaDoPeriodo(
                    400L,
                    List.of(ana),
                    List.of(),
                    limite(limitePorMes),
                    List.of(vaga(1, domingo, PROJECAO, ana), vaga(2, quinta, PROJECAO, ana)),
                    PesosDasRegras.de(regras));
        }
    }
}
