package br.igreja.escala.escala.solver;

import static br.igreja.escala.escala.solver.Cenario.EXPERIENTE;
import static br.igreja.escala.escala.solver.Cenario.INICIANTE;
import static br.igreja.escala.escala.solver.Cenario.PROJECAO;
import static br.igreja.escala.escala.solver.Cenario.TRANSMISSAO;
import static br.igreja.escala.escala.solver.Cenario.culto;
import static br.igreja.escala.escala.solver.Cenario.pessoa;
import static br.igreja.escala.escala.solver.Cenario.vaga;
import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosticoDaVagaTest {

    private final EventoDaEscala domingo = culto(1L, 4);
    private final List<CompromissoFixo> compromissos = new ArrayList<>();
    private ParametrosDaEscala parametros = Cenario.limite(3);
    private RegrasDoMinisterio regras = RegrasDoMinisterio.padrao();

    @Test
    void ninguemHabilitadoNaFuncao() {
        var soProjecao = pessoa(30, Map.of(PROJECAO.id(), EXPERIENTE), 1L);
        var vazia = vaga(1, domingo, TRANSMISSAO, null);

        assertThat(motivo(vazia, List.of(soProjecao), List.of(vazia)))
                .isEqualTo(new MotivoDaVagaVazia(TipoDeRegra.HABILITACAO, "Ninguém está habilitado em Transmissão."));
    }

    @Test
    void ninguemHabilitadoMarcouPode() {
        var semResposta = pessoa(30);
        var outroDia = pessoa(31, 2L);
        var vazia = vaga(1, domingo, PROJECAO, null);

        assertThat(motivo(vazia, List.of(semResposta, outroDia), List.of(vazia)))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.DISPONIBILIDADE, "Ninguém habilitado em Projeção marcou Pode."));
    }

    @Test
    void quemPodeJaServeEmOutraFuncaoDoEvento() {
        var ana = pessoa(30, 1L);
        var vazia = vaga(1, domingo, PROJECAO, null);

        assertThat(motivo(vazia, List.of(ana), List.of(vazia, vaga(2, domingo, TRANSMISSAO, ana))))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.UMA_FUNCAO_POR_EVENTO,
                        "A única pessoa que pode já serve em outra função neste evento."));
    }

    @Test
    void quemPodeJaServeEmOutroMinisterioNoHorarioSemDizerQual() {
        var ana = pessoa(30, 1L);
        compromissos.add(new CompromissoFixo(30L, 900L, domingo.inicio().plusMinutes(30), domingo.fim(), false));
        var vazia = vaga(1, domingo, PROJECAO, null);

        assertThat(motivo(vazia, List.of(ana), List.of(vazia)))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.SEM_SOBREPOSICAO,
                        "A única pessoa que pode já serve em outro evento nesse horário."));
    }

    @Test
    void quemPodeJaChegouAoLimiteDoMes() {
        var ana = pessoa(30, 1L, 2L, 3L, 4L);
        var bruno = pessoa(31, 1L, 2L, 3L, 4L);
        var vazia = vaga(1, domingo, PROJECAO, null);
        var vagas = new ArrayList<>(List.of(vazia));
        for (long dia = 2; dia <= 4; dia++) {
            var evento = culto(dia, (int) dia * 7);
            vagas.add(vaga(dia * 10, evento, PROJECAO, ana));
            vagas.add(vaga(dia * 10 + 1, evento, TRANSMISSAO, bruno));
        }

        assertThat(motivo(vazia, List.of(ana, bruno), vagas))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.LIMITE_POR_PERIODO, "As 2 pessoas que podem já têm 3 escalas no mês."));
    }

    @Test
    void limiteDeUmFalaNoSingular() {
        parametros = Cenario.limite(1);
        var ana = pessoa(30, 1L, 2L);
        var vazia = vaga(1, domingo, PROJECAO, null);

        assertThat(motivo(vazia, List.of(ana), List.of(vazia, vaga(2, culto(2L, 11), PROJECAO, ana))))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.LIMITE_POR_PERIODO, "A única pessoa que pode já tem 1 escala no mês."));
    }

    @Test
    void comOLimiteDesligadoAindaHaQuemPossa() {
        regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.LIMITE_POR_PERIODO, Rigidez.HARD, 1, false, new LimitePorPeriodoParams(1))));
        parametros = Cenario.limite(1);
        var ana = pessoa(30, 1L, 2L);
        var vazia = vaga(1, domingo, PROJECAO, null);

        assertThat(motivo(vazia, List.of(ana), List.of(vazia, vaga(2, culto(2L, 11), PROJECAO, ana))))
                .isEqualTo(new MotivoDaVagaVazia(
                        null,
                        "Ainda há quem possa servir aqui, mas a geração não achou a tempo. Gere a escala de novo."));
    }

    @Test
    void quemPodeEInicianteEOEventoAindaNaoTemExperiente() {
        regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(EXPERIENTE, 1))));
        parametros = Cenario.minimoDeExperientes(1);
        var lucas = pessoa(30, Map.of(PROJECAO.id(), INICIANTE), 1L);
        var diego = pessoa(31, Map.of(TRANSMISSAO.id(), INICIANTE), 1L);
        var vazia = vaga(2, domingo, TRANSMISSAO, null);

        assertThat(motivo(vazia, List.of(lucas, diego), List.of(vaga(1, domingo, PROJECAO, lucas), vazia)))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO,
                        "O evento precisa de pelo menos 1 pessoa do nível Experiente, e a única pessoa que pode não é"
                                + " desse nível."));
    }

    @Test
    void inicianteCabeQuandoOEventoJaTemOExperiente() {
        regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(EXPERIENTE, 1))));
        parametros = Cenario.minimoDeExperientes(1);
        var ana = pessoa(30, Map.of(PROJECAO.id(), EXPERIENTE), 1L);
        var diego = pessoa(31, Map.of(TRANSMISSAO.id(), INICIANTE), 1L);
        var vazia = vaga(2, domingo, TRANSMISSAO, null);

        assertThat(motivo(vazia, List.of(ana, diego), List.of(vaga(1, domingo, PROJECAO, ana), vazia))
                        .regra())
                .isNull();
    }

    @Test
    void quandoFaltaMaisDeUmNinguemSozinhoCompleta() {
        regras = RegrasDoMinisterio.de(List.of(new RegraVigente(
                TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(EXPERIENTE, 2))));
        parametros = new ParametrosDaEscala(3, EXPERIENTE, 2);
        var ana = pessoa(30, Map.of(TRANSMISSAO.id(), EXPERIENTE), 1L);
        var vazia = vaga(2, domingo, TRANSMISSAO, null);

        assertThat(motivo(vazia, List.of(ana), List.of(vaga(1, domingo, PROJECAO, null), vazia)))
                .isEqualTo(new MotivoDaVagaVazia(
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO,
                        "O evento precisa de pelo menos 2 pessoas do nível Experiente e tem 0: uma pessoa sozinha não"
                                + " completa."));
    }

    private MotivoDaVagaVazia motivo(VagaPlanejada vazia, List<Pessoa> pessoas, List<VagaPlanejada> vagas) {
        var escala = new EscalaDoPeriodo(400L, pessoas, compromissos, parametros, vagas, PesosDasRegras.de(regras));
        return new DiagnosticoDaVaga(escala, regras, Map.of(INICIANTE, "Iniciante", EXPERIENTE, "Experiente"))
                .motivo(vazia);
    }
}
