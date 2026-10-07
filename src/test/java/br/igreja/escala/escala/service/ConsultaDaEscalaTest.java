package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConsultaDaEscalaTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());

    private final Evento domingoPassado = evento(500L, 4);
    private final Evento domingo = evento(501L, 11);
    private final Evento cancelado = evento(503L, 18);
    private final Evento casamento = evento(504L, 24);

    private final UsuarioResumo ana = pessoa(30L, "Ana Souza");
    private final UsuarioResumo bruno = pessoa(31L, "Bruno Alves");
    private final UsuarioResumo carla = pessoa(32L, "Carla Dias");

    ConsultaDaEscalaTest() {
        ReflectionTestUtils.setField(outubro, "disponibilidadeTravada", true);
        cancelado.cancelar();
        casamento.exigirFuncoes(Set.of(projecao.getId()));
    }

    @Test
    void mesSemEventosNaoTemPeriodo() {
        var pagina = ConsultaDaEscala.montar(dados(null, List.of()));

        assertThat(pagina.temPeriodo()).isFalse();
        assertThat(pagina.gerada()).isFalse();
    }

    @Test
    void mesAindaSemVagasNaoFoiGerado() {
        var pagina = ConsultaDaEscala.montar(dados(outubro, List.of()));

        assertThat(pagina.temPeriodo()).isTrue();
        assertThat(pagina.gerada()).isFalse();
        assertThat(pagina.travada()).isTrue();
    }

    @Test
    void gradeTemUmaLinhaPorEventoNaoCanceladoComAsVagasDeCadaFuncao() {
        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas()));

        assertThat(pagina.gerada()).isTrue();
        assertThat(pagina.status()).isEqualTo("Rascunho");
        assertThat(pagina.funcoes()).containsExactly("Projeção", "Transmissão");
        assertThat(pagina.linhas())
                .extracting(
                        LinhaDaGrade::dia, LinhaDaGrade::diaDaSemana, LinhaDaGrade::horario, LinhaDaGrade::comVagaVazia)
                .containsExactly(
                        tuple("04", "Dom", "18h00", false),
                        tuple("11", "Dom", "18h00", true),
                        tuple("24", "Sáb", "18h00", false));
        var domingo11 = pagina.linhas().get(1).celulas();
        assertThat(domingo11.get(0).vagas())
                .containsExactly(SlotDaGrade.de(3L, 0, "Bruno Alves", "Iniciante", false, false, null, null, false));
        assertThat(domingo11.get(1).vagas()).containsExactly(SlotDaGrade.vazia(4L, 0, true, false, false));
        var casamento24 = pagina.linhas().get(2).celulas();
        assertThat(casamento24.get(1).exigida()).as("casamento só com Projeção").isFalse();
    }

    @Test
    void alertasExplicamAVagaVaziaEDizemQuemSaiuDaEscalaEmOrdemDeData() {
        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas()));

        assertThat(pagina.alertas())
                .containsExactly(
                        new AlertaDaEscala(
                                "Transmissão, 11/10 · Dom · 18h00 · Culto 11",
                                "Ninguém habilitado em Transmissão marcou Pode.",
                                "Regra: DISPONIBILIDADE"),
                        new AlertaDaEscala(
                                "18/10 · Dom · 18h00 · Culto 18 foi cancelado",
                                "Bruno Alves (Projeção) saiu da escala. As vagas somem quando você gerar a escala de"
                                        + " novo.",
                                null));
    }

    @Test
    void avisosDoSolverViramAMetaDaVagaEUmAlertaEAsVagasPorVirSeAbrem() {
        var avisos = Map.of(
                3L, List.of(new Violacao(TipoDeRegra.LIMITE_POR_PERIODO, "Já tem 3 escalas no mês, e o limite é 3.")));

        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas()), avisos, true);

        assertThat(pagina.ajustavel()).isTrue();
        var doBruno = pagina.linhas().get(1).celulas().get(0).vagas().getFirst();
        assertThat(doBruno.meta()).isEqualTo("LIMITE_POR_PERIODO");
        assertThat(doBruno.isAlerta()).isTrue();
        assertThat(doBruno.editavel()).isTrue();
        assertThat(pagina.linhas()
                        .getFirst()
                        .celulas()
                        .getFirst()
                        .vagas()
                        .getFirst()
                        .editavel())
                .as("o evento do dia 4 já passou")
                .isFalse();
        assertThat(pagina.alertas())
                .contains(new AlertaDaEscala(
                        "Projeção, 11/10 · Dom · 18h00 · Culto 11 — Bruno Alves fora da regra",
                        "Já tem 3 escalas no mês, e o limite é 3. Troque a pessoa ou force a vaga com uma"
                                + " justificativa.",
                        "Regra: LIMITE_POR_PERIODO"));
    }

    @Test
    void vagaForcadaMostraARegraEAJustificativaSemAlerta() {
        var vagas = vagas();
        vagas.get(2).forcar(bruno.id(), "Combinou por telefone");
        var avisos = Map.of(3L, List.of(new Violacao(TipoDeRegra.DISPONIBILIDADE, "Não marcou Pode neste evento.")));

        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas), avisos, true);

        var doBruno = pagina.linhas().get(1).celulas().get(0).vagas().getFirst();
        assertThat(doBruno.meta()).isEqualTo("Forçada · DISPONIBILIDADE");
        assertThat(doBruno.justificativa()).isEqualTo("Combinou por telefone");
        assertThat(doBruno.descricao())
                .isEqualTo("Bruno Alves, Forçada · DISPONIBILIDADE. Justificativa: Combinou por telefone");
        assertThat(pagina.alertas()).noneMatch(alerta -> alerta.titulo().contains("fora da regra"));
    }

    @Test
    void desistenciaViraUmAlertaComQuemFuncaoEventoEAAcaoDePreencherNoLugarDoDeVagaVazia() {
        var vagas = vagas();
        vagas.get(2).desistir(Instant.parse("2026-10-05T17:32:00Z"));

        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas));

        assertThat(pagina.alertas())
                .containsExactly(
                        new AlertaDaEscala(
                                "Bruno Alves desistiu de Projeção, 11/10 · Dom · 18h00 · Culto 11",
                                "A vaga está vazia desde 05/10 às 14h32. Escolha quem entra no lugar: o ajuste confere"
                                        + " as regras.",
                                null,
                                "arrow-left-right",
                                3L),
                        new AlertaDaEscala(
                                "Transmissão, 11/10 · Dom · 18h00 · Culto 11",
                                "Ninguém habilitado em Transmissão marcou Pode.",
                                "Regra: DISPONIBILIDADE"),
                        new AlertaDaEscala(
                                "18/10 · Dom · 18h00 · Culto 18 foi cancelado",
                                "Bruno Alves (Projeção) saiu da escala. As vagas somem quando você gerar a escala de"
                                        + " novo.",
                                null));
        assertThat(pagina.linhas().get(1).celulas().get(0).vagas().getFirst().vazia())
                .isTrue();
    }

    @Test
    void desistenciaDeQuemNaoServeMaisMostraONome() {
        var vagas = vagas();
        vagas.get(5).escalar(33L);
        vagas.get(5).desistir(Instant.parse("2026-10-05T17:32:00Z"));

        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas, List.of(pessoa(33L, "Davi Rocha"))));

        assertThat(pagina.alertas())
                .extracting(AlertaDaEscala::titulo)
                .contains("Davi Rocha desistiu de Projeção, 24/10 · Sáb · 18h00 · Culto 24");
    }

    @Test
    void desistenciaSaiDoAlertaQuandoAlguemEntraNoLugarOuOEventoPassa() {
        var vagas = vagas();
        vagas.get(2).desistir(Instant.parse("2026-10-05T17:32:00Z"));
        vagas.get(2).ajustar(carla.id());
        vagas.getFirst().desistir(Instant.parse("2026-10-03T12:00:00Z"));

        var pagina = ConsultaDaEscala.montar(dados(outubro, vagas));

        assertThat(pagina.alertas()).noneMatch(alerta -> alerta.titulo().contains("desistiu"));
    }

    @Test
    void soAEscalaPublicadaVistaPeloGerenteTemOTextoDoWhatsappDeCadaEvento() {
        var vagas = vagas();
        vagas.get(2).desistir(Instant.parse("2026-10-05T17:32:00Z"));
        vagas.get(5).ajustar(carla.id());

        var rascunho = ConsultaDaEscala.montar(dados(outubro, vagas));
        outubro.publicarEscala();
        var publicada = ConsultaDaEscala.montar(dados(outubro, vagas));
        var doMembro = ConsultaDaEscala.paraOMembro(dados(outubro, vagas));

        assertThat(rascunho.linhas()).allMatch(linha -> linha.whatsapp() == null);
        assertThat(doMembro.linhas()).allMatch(linha -> linha.whatsapp() == null);
        assertThat(publicada.linhas())
                .extracting(LinhaDaGrade::whatsapp)
                .containsExactly(
                        new LinhaDaGrade.Whatsapp(
                                "whatsapp-500",
                                "*Mídia — Culto 4*\n04/10 · Dom · 18h00\n\nProjeção: Ana Souza\nTransmissão: a definir"),
                        new LinhaDaGrade.Whatsapp(
                                "whatsapp-501",
                                "*Mídia — Culto 11*\n11/10 · Dom · 18h00\n\nProjeção: a definir\nTransmissão: a"
                                        + " definir"),
                        new LinhaDaGrade.Whatsapp(
                                "whatsapp-504", "*Mídia — Culto 24*\n24/10 · Sáb · 18h00\n\nProjeção: Carla Dias"));
    }

    @Test
    void resumoContaAsVagasDoMesEAsEscalasDeCadaPessoa() {
        var resumo = ConsultaDaEscala.montar(dados(outubro, vagas())).resumo();

        assertThat(resumo.vagas()).isEqualTo(5);
        assertThat(resumo.preenchidas()).isEqualTo(3);
        assertThat(resumo.vazias()).isEqualTo(2);
        assertThat(resumo.eventos()).isEqualTo(3);
        assertThat(resumo.maiorCarga()).isEqualTo(2);
        assertThat(resumo.porPessoa())
                .extracting(CargaDaPessoa::nome, CargaDaPessoa::descricao)
                .containsExactly(
                        tuple("Ana Souza", "2 escalas"),
                        tuple("Bruno Alves", "1 escala"),
                        tuple("Carla Dias", "Nenhuma escala"));
    }

    /**
     * 04/10 (passou): Ana e Transmissão vazia. 11/10: Bruno e Transmissão vazia. 18/10 (cancelado): Bruno. 24/10
     * (casamento, só Projeção): Ana.
     */
    private List<Vaga> vagas() {
        return Stream.of(
                        vaga(1L, domingoPassado, projecao, ana.id()),
                        vaga(2L, domingoPassado, transmissao, null),
                        vaga(3L, domingo, projecao, bruno.id()),
                        vaga(4L, domingo, transmissao, null),
                        vaga(5L, cancelado, projecao, bruno.id()),
                        vaga(6L, casamento, projecao, ana.id()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private DadosDoPeriodo dados(Periodo periodo, List<Vaga> vagas) {
        return dados(periodo, vagas, List.of());
    }

    private DadosDoPeriodo dados(Periodo periodo, List<Vaga> vagas, List<UsuarioResumo> desistentes) {
        return new DadosDoPeriodo(
                1L,
                "Mídia",
                OUTUBRO,
                periodo,
                List.of(domingoPassado, domingo, cancelado, casamento),
                List.of(projecao, transmissao),
                vagas,
                List.of(ana, bruno, carla),
                List.of(),
                Map.of(501L, Set.of(31L), 504L, Set.of(30L)),
                List.of(
                        new HabilitacaoDaPessoa(30L, 100L, 201L),
                        new HabilitacaoDaPessoa(30L, 101L, 201L),
                        new HabilitacaoDaPessoa(31L, 100L, 200L),
                        new HabilitacaoDaPessoa(32L, 101L, 201L)),
                Map.of(200L, "Iniciante", 201L, "Experiente"),
                RegrasDoMinisterio.padrao(),
                List.of(),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                desistentes);
    }

    private Evento evento(Long id, int dia) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, "Culto " + dia, LocalDate.of(2026, 10, dia), LocalTime.of(18, 0), DUAS_HORAS),
                id);
    }

    private static Vaga vaga(Long id, Evento evento, Funcao funcao, Long usuarioId) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), 1), id);
        vaga.escalar(usuarioId);
        return vaga;
    }

    private static UsuarioResumo pessoa(Long id, String nome) {
        return new UsuarioResumo(id, nome, id + "@teste.local", null, false, false, true);
    }
}
