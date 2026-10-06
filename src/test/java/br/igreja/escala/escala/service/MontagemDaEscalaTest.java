package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.CompromissoFixo;
import br.igreja.escala.escala.solver.Pessoa;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MontagemDaEscalaTest {

    /** Quarta, 7 de outubro de 2026, 10h em São Paulo. */
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 7, 10, 0);

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());

    private final Evento domingoPassado = evento(500L, 4);
    private final Evento domingo = evento(501L, 11);
    private final Evento quintaSoProjecao = evento(502L, 15);
    private final Evento cancelado = evento(503L, 18);

    private final UsuarioResumo ana = pessoa(30L, "Ana Souza");
    private final UsuarioResumo bruno = pessoa(31L, "Bruno Alves");
    private final UsuarioResumo carla = pessoa(32L, "Carla Dias");

    private final List<CompromissoFixo> noLouvor = List.of(new CompromissoFixo(
            30L, 900L, LocalDateTime.of(2026, 10, 11, 17, 0), LocalDateTime.of(2026, 10, 11, 19, 0), false));

    MontagemDaEscalaTest() {
        quintaSoProjecao.exigirFuncoes(Set.of(projecao.getId()));
        cancelado.cancelar();
    }

    @Test
    void criaSoAsVagasQueFaltamNosEventosPorVirSemCanceladoNemPassado() {
        var jaTem = vaga(2L, domingo, projecao, 1, bruno.id());

        var reconciliacao = MontagemDaEscala.reconciliar(dados(List.of(jaTem)));

        assertThat(reconciliacao.novas())
                .extracting(Vaga::getEventoId, Vaga::getFuncaoId, Vaga::getPosicao)
                .containsExactlyInAnyOrder(tuple(501L, 101L, 1), tuple(502L, 100L, 1));
        assertThat(reconciliacao.apagar()).isEmpty();
    }

    @Test
    void apagaAsVagasQueSairamDaEscalaENaoMexeNoPassado() {
        var doPassado = vaga(1L, domingoPassado, projecao, 1, ana.id());
        var funcaoQueNaoPrecisaMais = vaga(4L, quintaSoProjecao, transmissao, 1, ana.id());
        var doCancelado = vaga(5L, cancelado, projecao, 1, bruno.id());
        var acimaDoMaximo = vaga(6L, domingo, projecao, 2, null);

        var reconciliacao = MontagemDaEscala.reconciliar(
                dados(List.of(doPassado, funcaoQueNaoPrecisaMais, doCancelado, acimaDoMaximo)));

        assertThat(reconciliacao.apagar())
                .containsExactlyInAnyOrder(funcaoQueNaoPrecisaMais, doCancelado, acimaDoMaximo);
    }

    @Test
    void recomecarEsvaziaAsSoltasEMantemAsPresas() {
        var solta = vaga(2L, domingo, projecao, 1, bruno.id());
        var fixada = vaga(3L, domingo, transmissao, 1, carla.id());
        ReflectionTestUtils.setField(fixada, "fixada", true);

        var escala = MontagemDaEscala.montar(dados(List.of(solta, fixada)), true);

        assertThat(escala.getVagas())
                .extracting(VagaPlanejada::getId, VagaPlanejada::isPresa, vaga -> nome(vaga.getPessoa()))
                .containsExactly(tuple(2L, false, null), tuple(3L, true, "Carla Dias"));
        assertThat(escala.getPeriodoId()).isEqualTo(400L);
    }

    @Test
    void semRecomecarAsVagasFicamComoEstao() {
        var escala = MontagemDaEscala.montar(dados(List.of(vaga(2L, domingo, projecao, 1, bruno.id()))), false);

        assertThat(escala.getVagas())
                .singleElement()
                .extracting(vaga -> nome(vaga.getPessoa()))
                .isEqualTo("Bruno Alves");
    }

    @Test
    void oPassadoDoMesContaComoCompromissoEOsOutrosMinisteriosTambem() {
        var doPassado = vaga(1L, domingoPassado, projecao, 1, ana.id());

        var escala = MontagemDaEscala.montar(dados(List.of(doPassado)), true);

        assertThat(escala.getVagas()).isEmpty();
        assertThat(escala.getCompromissos())
                .containsExactlyInAnyOrder(
                        noLouvor.getFirst(),
                        new CompromissoFixo(30L, 500L, domingoPassado.getInicio(), domingoPassado.getFim(), true));
    }

    @Test
    void pessoasLevamONivelEmCadaFuncaoEOsEventosEmQueMarcaramPode() {
        var escala = MontagemDaEscala.montar(dados(List.of()), true);

        assertThat(escala.getPessoas())
                .extracting(Pessoa::id, Pessoa::nivelPorFuncao, Pessoa::eventosQuePode)
                .containsExactly(
                        tuple(30L, Map.of(100L, 201L, 101L, 201L), Set.of(501L, 502L)),
                        tuple(31L, Map.of(100L, 200L), Set.of(501L)),
                        tuple(32L, Map.of(101L, 201L), Set.of()));
    }

    @Test
    void quemNaoServeMaisEntraSemHabilitacaoNemDisponibilidade() {
        var deDiego = vaga(2L, domingo, projecao, 1, 33L);
        ReflectionTestUtils.setField(deDiego, "fixada", true);
        var dados = dados(List.of(deDiego));
        var semDiego = new DadosDoPeriodo(
                dados.ministerioId(),
                dados.nomeDoMinisterio(),
                dados.mes(),
                dados.periodo(),
                dados.eventos(),
                dados.funcoes(),
                dados.vagas(),
                dados.quemServe(),
                List.of(pessoa(33L, "Diego Martins")),
                dados.quemPode(),
                dados.habilitacoes(),
                dados.nomesDosNiveis(),
                dados.regras(),
                dados.compromissosEmOutrosMinisterios(),
                dados.agora(),
                List.of());

        var escala = MontagemDaEscala.montar(semDiego, true);

        assertThat(escala.getVagas().getFirst().getPessoa())
                .isEqualTo(new Pessoa(33L, "Diego Martins", Map.of(), Set.of()));
    }

    @Test
    void vagaVigenteEDeEventoNaoCanceladoComFuncaoExigidaEPosicaoAteOMaximo() {
        assertThat(MontagemDaEscala.vigente(vaga(1L, domingo, projecao, 1, null), domingo, projecao))
                .isTrue();
        assertThat(MontagemDaEscala.vigente(vaga(1L, domingo, projecao, 2, null), domingo, projecao))
                .isFalse();
        assertThat(MontagemDaEscala.vigente(vaga(1L, cancelado, projecao, 1, null), cancelado, projecao))
                .isFalse();
        assertThat(MontagemDaEscala.vigente(
                        vaga(1L, quintaSoProjecao, transmissao, 1, null), quintaSoProjecao, transmissao))
                .isFalse();
        assertThat(MontagemDaEscala.vigente(vaga(1L, domingo, projecao, 1, null), domingo, null))
                .isFalse();
    }

    private DadosDoPeriodo dados(List<Vaga> vagas) {
        Map<Long, Set<Long>> quemPode = new HashMap<>();
        quemPode.put(501L, Set.of(30L, 31L));
        quemPode.put(502L, Set.of(30L));
        return new DadosDoPeriodo(
                1L,
                "Mídia",
                OUTUBRO,
                outubro,
                List.of(domingoPassado, domingo, quintaSoProjecao, cancelado),
                List.of(projecao, transmissao),
                new ArrayList<>(vagas),
                List.of(ana, bruno, carla),
                List.of(),
                quemPode,
                List.of(
                        new HabilitacaoDaPessoa(30L, 100L, 201L),
                        new HabilitacaoDaPessoa(30L, 101L, 201L),
                        new HabilitacaoDaPessoa(31L, 100L, 200L),
                        new HabilitacaoDaPessoa(32L, 101L, 201L)),
                Map.of(200L, "Iniciante", 201L, "Experiente"),
                RegrasDoMinisterio.padrao(),
                noLouvor,
                AGORA,
                List.of());
    }

    private Evento evento(Long id, int dia) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, "Culto " + dia, LocalDate.of(2026, 10, dia), LocalTime.of(18, 0), DUAS_HORAS),
                id);
    }

    private static Vaga vaga(Long id, Evento evento, Funcao funcao, int posicao, Long usuarioId) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), posicao), id);
        vaga.escalar(usuarioId);
        return vaga;
    }

    private static UsuarioResumo pessoa(Long id, String nome) {
        return new UsuarioResumo(id, nome, id + "@teste.local", null, false, false, true);
    }

    private static String nome(Pessoa pessoa) {
        return pessoa == null ? null : pessoa.nome();
    }
}
