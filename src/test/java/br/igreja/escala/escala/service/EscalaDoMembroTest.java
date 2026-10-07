package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.web.MinhasEscalas;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hoje é 07/10/2026, 10h. A Ana (membro, id 20) está em outubro (publicada) nos dias 4 (passou) e 11 e 18, em setembro
 * (publicada) no dia 6 e em novembro (rascunho) no dia 1; e numa vaga de uma função que o evento não exige mais.
 */
class EscalaDoMembroTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final Long ANA = AcessoDeTeste.MEMBRO.getId();
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final VagaRepository vagas = mock(VagaRepository.class);
    private final EventoService eventos = mock(EventoService.class);
    private final FuncaoService funcoes = mock(FuncaoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final MembroService membros = mock(MembroService.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final LeituraDoPeriodo leitura = mock(LeituraDoPeriodo.class);
    private final EscalaDoMembro servico = new EscalaDoMembro(
            vagas,
            eventos,
            funcoes,
            ministerios,
            membros,
            periodos,
            leitura,
            Clock.fixed(
                    ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO));

    private final Periodo setembro = periodo(399L, YearMonth.of(2026, 9), true);
    private final Periodo outubro = periodo(400L, OUTUBRO, true);
    private final Periodo novembro = periodo(401L, NOVEMBRO, false);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());
    private final Evento dia6set = evento(490L, setembro, LocalDate.of(2026, 9, 6));
    private final Evento dia4 = evento(500L, outubro, LocalDate.of(2026, 10, 4));
    private final Evento dia11 = evento(501L, outubro, LocalDate.of(2026, 10, 11));
    private final Evento dia18 = evento(502L, outubro, LocalDate.of(2026, 10, 18));
    private final Evento dia1nov = evento(510L, novembro, LocalDate.of(2026, 11, 1));

    @BeforeEach
    void prepara() {
        dia18.exigirFuncoes(Set.of(projecao.getId()));
        when(vagas.findByUsuarioId(ANA))
                .thenReturn(List.of(
                        vaga(1L, dia18, projecao),
                        vaga(2L, dia4, transmissao),
                        vaga(3L, dia11, projecao),
                        vaga(4L, dia1nov, projecao),
                        vaga(5L, dia6set, projecao),
                        vaga(6L, dia18, transmissao)));
        when(eventos.porIds(anyCollection())).thenReturn(List.of(dia6set, dia4, dia11, dia18, dia1nov));
        when(funcoes.porIds(anyCollection())).thenReturn(List.of(projecao, transmissao));
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
        when(membros.ministeriosDe(ANA)).thenReturn(List.of(Exemplos.midia()));
    }

    @Test
    void minhasEscalasSoDasPublicadasProximasPrimeiroEPassadasRecentes() {
        var minhas = servico.doMembro(ANA);

        assertThat(minhas.proximas())
                .containsExactly(
                        new MinhasEscalas.Escala(
                                "11/10 · Dom · 18h00",
                                "Projeção",
                                "Culto",
                                "Mídia",
                                "mint",
                                "/escalas/1?mes=2026-10",
                                3L,
                                true),
                        new MinhasEscalas.Escala(
                                "18/10 · Dom · 18h00",
                                "Projeção",
                                "Culto",
                                "Mídia",
                                "mint",
                                "/escalas/1?mes=2026-10",
                                1L,
                                true));
        assertThat(minhas.passadas())
                .extracting(MinhasEscalas.Escala::quando)
                .as("a mais recente antes; a de setembro ainda está nos 60 dias")
                .containsExactly("04/10 · Dom · 18h00", "06/09 · Dom · 18h00");
        assertThat(minhas.ministerios()).containsExactly(new MinhasEscalas.Ministerio("Mídia", "mint", "/escalas/1"));
        assertThat(minhas.passadas()).noneMatch(MinhasEscalas.Escala::podeDesistir);
    }

    @Test
    void comMenosDe24HorasOuJaComecadaSoOGerenteMuda() {
        var amanhaAs10 = evento(503L, outubro, LocalDate.of(2026, 10, 8), LocalTime.of(10, 0));
        var amanhaAs9 = evento(504L, outubro, LocalDate.of(2026, 10, 8), LocalTime.of(9, 59));
        var agora = evento(505L, outubro, LocalDate.of(2026, 10, 7), LocalTime.of(9, 0));
        when(vagas.findByUsuarioId(ANA))
                .thenReturn(List.of(
                        vaga(7L, amanhaAs10, projecao), vaga(8L, amanhaAs9, projecao), vaga(9L, agora, projecao)));
        when(eventos.porIds(anyCollection())).thenReturn(List.of(amanhaAs10, amanhaAs9, agora));

        var minhas = servico.doMembro(ANA);

        assertThat(minhas.proximas())
                .extracting(MinhasEscalas.Escala::vagaId, MinhasEscalas.Escala::podeDesistir)
                .as("a que está acontecendo continua nas próximas, sem desistir")
                .containsExactly(tuple(9L, false), tuple(8L, false), tuple(7L, true));
    }

    @Test
    void escalaDoMinisterioPublicadaTemAGradeSemOQueEDoGerente() {
        var forcada = vaga(3L, dia11, projecao);
        forcada.forcar(ANA, "Combinou por telefone");
        when(membros.participa(ANA, MIDIA)).thenReturn(true);
        when(periodos.doMes(MIDIA, OUTUBRO)).thenReturn(Optional.of(outubro));
        when(leitura.ler(MIDIA, OUTUBRO)).thenReturn(dados(List.of(forcada)));

        var escala = servico.doMinisterio(MIDIA, OUTUBRO, AcessoDeTeste.MEMBRO);

        assertThat(escala.publicada()).isTrue();
        assertThat(escala.tint()).isEqualTo("mint");
        var slot = escala.linhas().get(1).celulas().getFirst().vagas().getFirst();
        assertThat(slot.nome()).isEqualTo("Ana Souza");
        assertThat(slot.forcada()).isFalse();
        assertThat(slot.fixada()).isFalse();
        assertThat(slot.justificativa()).isNull();
        assertThat(slot.editavel()).isFalse();
    }

    @Test
    void rascunhoNaoMostraNadaDaEscala() {
        when(membros.participa(ANA, MIDIA)).thenReturn(true);
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));

        var escala = servico.doMinisterio(MIDIA, NOVEMBRO, AcessoDeTeste.MEMBRO);

        assertThat(escala.publicada()).isFalse();
        assertThat(escala.linhas()).isEmpty();
        verify(leitura, never()).ler(any(), any());
    }

    @Test
    void ministerioDeQueNaoEMembroE404MasOAdminVe() {
        when(membros.participa(ANA, MIDIA)).thenReturn(false);
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembro));

        assertThatThrownBy(() -> servico.doMinisterio(MIDIA, NOVEMBRO, AcessoDeTeste.MEMBRO))
                .isInstanceOf(NaoEncontradoException.class);
        assertThat(servico.doMinisterio(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN).nome())
                .isEqualTo("Mídia");
    }

    private DadosDoPeriodo dados(List<Vaga> doMes) {
        return new DadosDoPeriodo(
                MIDIA,
                "Mídia",
                OUTUBRO,
                outubro,
                List.of(dia4, dia11, dia18),
                List.of(projecao, transmissao),
                doMes,
                List.of(new UsuarioResumo(ANA, "Ana Souza", "ana@x", null, false, false, true)),
                List.of(),
                Map.of(),
                List.of(new HabilitacaoDaPessoa(ANA, projecao.getId(), 201L)),
                Map.of(201L, "Experiente"),
                RegrasDoMinisterio.padrao(),
                List.of(),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                List.of());
    }

    private static Periodo periodo(Long id, YearMonth mes, boolean publicada) {
        var periodo = ExemplosDeEvento.comId(ExemplosDeEvento.periodo(MIDIA, mes), id);
        if (publicada) {
            periodo.publicarEscala();
        }
        return periodo;
    }

    private static Evento evento(Long id, Periodo periodo, LocalDate data) {
        return evento(id, periodo, data, LocalTime.of(18, 0));
    }

    private static Evento evento(Long id, Periodo periodo, LocalDate data, LocalTime horario) {
        return ExemplosDeEvento.comId(Evento.avulso(periodo, "Culto", data, horario, DUAS_HORAS), id);
    }

    private Vaga vaga(Long id, Evento evento, Funcao funcao) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), 1), id);
        vaga.escalar(ANA);
        return vaga;
    }
}
