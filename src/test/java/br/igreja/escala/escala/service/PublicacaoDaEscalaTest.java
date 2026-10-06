package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.service.ResumoDaPublicacao.Item;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.RestricoesDaEscala;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Outubro de 2026, hoje é 07/10: culto do dia 11 com Ana em Projeção e Transmissão vazia; dia 18 com Carla forçada. */
class PublicacaoDaEscalaTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final long GERENTE = AcessoDeTeste.GERENTE_DA_MIDIA.getId();
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final LeituraDoPeriodo leitura = mock(LeituraDoPeriodo.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final GeracoesEmAndamento andamentos = new GeracoesEmAndamento();
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final PublicacaoDaEscala publicacao = new PublicacaoDaEscala(
            leitura,
            periodos,
            andamentos,
            SolutionManager.create(SolverFactory.create(new SolverConfig()
                    .withSolutionClass(EscalaDoPeriodo.class)
                    .withEntityClasses(VagaPlanejada.class)
                    .withConstraintProviderClass(RestricoesDaEscala.class))),
            auditoria,
            ministerios);

    private final Periodo outubro = ExemplosDeEvento.comId(ExemplosDeEvento.periodo(MIDIA, OUTUBRO), 400L);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());
    private final Evento dia11 = evento(501L, 11);
    private final Evento dia18 = evento(502L, 18);
    private final UsuarioResumo ana = pessoa(30L, "Ana Souza");
    private final UsuarioResumo carla = pessoa(32L, "Carla Dias");
    private final List<Vaga> vagas = new ArrayList<>();

    @BeforeEach
    void prepara() {
        when(periodos.doMes(MIDIA, OUTUBRO)).thenReturn(Optional.of(outubro));
        when(leitura.ler(MIDIA, OUTUBRO)).thenAnswer(chamada -> dados());
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
        vaga(1L, dia11, projecao).escalar(ana.id());
        vaga(2L, dia11, transmissao);
        vaga(3L, dia18, projecao).forcar(carla.id(), "Combinou por telefone");
        vaga(4L, dia18, transmissao).escalar(ana.id());
    }

    @Test
    void resumoListaAsVaziasAsForcadasEAsRegrasQueMaisPesam() {
        var resumo = publicacao.resumo(MIDIA, OUTUBRO);

        assertThat(resumo.temVazias()).isTrue();
        assertThat(resumo.vazias())
                .containsExactly(new Item(
                        "Transmissão, 11/10 · Dom · 18h00 · Culto 11",
                        "A única pessoa que pode já serve em outra função neste evento."));
        assertThat(resumo.forcadas())
                .containsExactly(new Item(
                        "Carla Dias em Projeção, 18/10 · Dom · 18h00 · Culto 18",
                        "DISPONIBILIDADE · Combinou por telefone"));
        assertThat(resumo.regras())
                .containsExactly(
                        new Item(
                                "Eventos mais próximos primeiro · PRIORIDADE_POR_DATA",
                                "1 vaga vazia, as mais próximas pesam mais."),
                        new Item(
                                "Equilíbrio · EQUILIBRIO_DE_CARGA",
                                "Quem mais serve tem 2 escalas por vir; quem menos, 1."));
    }

    @Test
    void publicaMesmoComVagaVaziaEAudita() {
        when(periodos.publicarEscala(400L)).thenReturn(true);

        assertThat(publicacao.publicar(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isEqualTo("Escala de outubro publicada");

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.PUBLICAR_ESCALA,
                        GERENTE,
                        MIDIA,
                        null,
                        "Escala de Outubro 2026 publicada (Mídia): 3 de 4 vagas por vir preenchidas, 1 obrigatória"
                                + " vazia, 1 forçada."));
    }

    @Test
    void jaPublicadaNaoAuditaDeNovo() {
        when(periodos.publicarEscala(400L)).thenReturn(false);

        assertThat(publicacao.publicar(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isEqualTo("A escala de outubro já estava publicada");
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void naoPublicaSemGerarNemComAGeracaoRodando() {
        vagas.clear();
        assertThatThrownBy(() -> publicacao.publicar(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Gere a escala de outubro antes de publicar.");

        andamentos.registrar(new Andamento(400L, MIDIA, OUTUBRO, GERENTE, Instant.now()));
        assertThatThrownBy(() -> publicacao.publicar(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .hasMessage("A escala de outubro está sendo gerada. Espere terminar para publicar.");
        verify(periodos, never()).publicarEscala(anyLong());
    }

    @Test
    void reabreParaRascunhoEAudita() {
        when(periodos.reabrirEscala(400L)).thenReturn(true, false);

        assertThat(publicacao.reabrir(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isEqualTo("Escala de outubro reaberta para rascunho");
        assertThat(publicacao.reabrir(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isEqualTo("A escala de outubro já era rascunho");

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.REABRIR_ESCALA,
                        GERENTE,
                        MIDIA,
                        null,
                        "Escala de Outubro 2026 reaberta para rascunho (Mídia): saiu da visão dos membros."));
    }

    @Test
    void mesSemEventosNaoTemOQuePublicar() {
        when(periodos.doMes(MIDIA, OUTUBRO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publicacao.reabrir(MIDIA, OUTUBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .hasMessage("Outubro 2026 ainda não tem eventos: não há escala para reabrir.");
    }

    private Vaga vaga(Long id, Evento evento, Funcao funcao) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), 1), id);
        vagas.add(vaga);
        return vaga;
    }

    private DadosDoPeriodo dados() {
        return new DadosDoPeriodo(
                MIDIA,
                "Mídia",
                OUTUBRO,
                outubro,
                List.of(dia11, dia18),
                List.of(projecao, transmissao),
                List.copyOf(vagas),
                List.of(ana, carla),
                List.of(),
                Map.of(dia11.getId(), Set.of(ana.id()), dia18.getId(), Set.of(ana.id())),
                List.of(
                        new HabilitacaoDaPessoa(ana.id(), projecao.getId(), 201L),
                        new HabilitacaoDaPessoa(ana.id(), transmissao.getId(), 201L),
                        new HabilitacaoDaPessoa(carla.id(), projecao.getId(), 201L)),
                Map.of(200L, "Iniciante", 201L, "Experiente"),
                RegrasDoMinisterio.padrao(),
                List.of(),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                List.of());
    }

    private Evento evento(Long id, int dia) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, "Culto " + dia, LocalDate.of(2026, 10, dia), LocalTime.of(18, 0), DUAS_HORAS),
                id);
    }

    private static UsuarioResumo pessoa(Long id, String nome) {
        return new UsuarioResumo(id, nome, id + "@teste.local", null, false, false, true);
    }
}
