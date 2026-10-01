package br.igreja.escala.disponibilidade.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.service.MembroService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsultaDaDisponibilidadeTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final long LOUVOR = AcessoDeTeste.LOUVOR;
    private static final long ANA = 30L;
    private static final long PAULA = 10L;
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);
    private static final UsuarioResumo ANA_SOUZA =
            new UsuarioResumo(ANA, "Ana Souza", "ana@x.com", null, false, false, true);
    private static final UsuarioResumo BRUNO =
            new UsuarioResumo(31L, "Bruno Lima", "bruno@x.com", null, false, false, true);
    private static final UsuarioResumo CARLA =
            new UsuarioResumo(32L, "Carla Dias", "carla@x.com", null, false, false, true);

    private final DisponibilidadeRepository disponibilidades = mock(DisponibilidadeRepository.class);
    private final EventoService eventos = mock(EventoService.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final MembroService membros = mock(MembroService.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final ConsultaDaDisponibilidade consulta =
            new ConsultaDaDisponibilidade(disponibilidades, eventos, periodos, membros, usuarios);

    private final Periodo novembroDaMidia = ExemplosDeEvento.periodo(MIDIA, NOVEMBRO);
    private final Periodo novembroDoLouvor = ExemplosDeEvento.comId(new Periodo(LOUVOR, NOVEMBRO), 401L);
    private final Evento manha = evento(novembroDaMidia, 500L, "Culto da manhã", 1, LocalTime.of(9, 30));
    private final Evento noite = evento(novembroDaMidia, 501L, "Culto de domingo", 1, LocalTime.of(18, 0));
    private final Evento quinta = evento(novembroDaMidia, 502L, "Culto de quinta", 5, LocalTime.of(19, 30));
    private final Evento ensaio = evento(novembroDoLouvor, 600L, "Ensaio do louvor", 7, LocalTime.of(16, 0));

    @BeforeEach
    void prepara() {
        when(membros.ministeriosEmQueServe(ANA)).thenReturn(List.of(Exemplos.louvor(), Exemplos.midia()));
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.of(novembroDaMidia));
        when(periodos.doMes(LOUVOR, NOVEMBRO)).thenReturn(Optional.of(novembroDoLouvor));
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO)).thenReturn(List.of(manha, noite, quinta));
        when(eventos.porVirDoMes(LOUVOR, NOVEMBRO)).thenReturn(List.of(ensaio));
        when(usuarios.resumosPorId(Set.of(PAULA)))
                .thenReturn(Map.of(
                        PAULA, new UsuarioResumo(PAULA, "Paula Ribeiro", "paula@x.com", null, false, false, true)));
    }

    @Test
    void umGrupoPorMinisterioComOsEventosDoMesmoDiaSeparadosPeloHorario() {
        var pode = new Disponibilidade(ANA, manha, Resposta.PODE, ANA);
        var naoPode = new Disponibilidade(ANA, quinta, Resposta.NAO_PODE, ANA);
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(ANA, List.of(500L, 501L, 502L)))
                .thenReturn(List.of(pode, naoPode));
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(ANA, List.of(600L))).thenReturn(List.of());

        var tela = consulta.doMembro(ANA, NOVEMBRO);

        assertThat(tela.serveEmAlgum()).isTrue();
        assertThat(tela.titulo()).isEqualTo("Disponibilidade — Novembro");
        assertThat(tela.grupos())
                .extracting(GrupoDeDisponibilidade::ministerio, GrupoDeDisponibilidade::tint)
                .containsExactly(tuple("Louvor", "rose"), tuple("Mídia", "mint"));
        var midia = tela.grupo(MIDIA).orElseThrow();
        assertThat(midia.linhas())
                .extracting(
                        LinhaDeDisponibilidade::dia,
                        LinhaDeDisponibilidade::diaDaSemana,
                        LinhaDeDisponibilidade::nome,
                        LinhaDeDisponibilidade::horario,
                        LinhaDeDisponibilidade::resposta)
                .containsExactly(
                        tuple("01", "Dom", "Culto da manhã", "09h30", Resposta.PODE),
                        tuple("01", "Dom", "Culto de domingo", "18h00", null),
                        tuple("05", "Qui", "Culto de quinta", "19h30", Resposta.NAO_PODE));
        assertThat(midia.contagem()).isEqualTo("2 de 3 respondidos");
        assertThat(tela.grupo(LOUVOR).orElseThrow().contagem()).isEqualTo("0 de 1 respondido");
        assertThat(tela.contagem()).isEqualTo("2 de 4 respondidos");
        assertThat(midia.linhas().get(0).descricao()).isEqualTo("01/11 · Dom · Culto da manhã, 09h30");
    }

    @Test
    void grupoTravadoNaoEEditavelParaOMembro() {
        novembroDoLouvor.travarDisponibilidade();
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(any(), any())).thenReturn(List.of());

        var tela = consulta.doMembro(ANA, NOVEMBRO);

        assertThat(tela.grupo(LOUVOR).orElseThrow())
                .extracting(GrupoDeDisponibilidade::travado, GrupoDeDisponibilidade::editavel)
                .containsExactly(true, false);
        assertThat(tela.grupo(MIDIA).orElseThrow())
                .extracting(GrupoDeDisponibilidade::travado, GrupoDeDisponibilidade::editavel)
                .containsExactly(false, true);
    }

    @Test
    void mostraQuemMarcouEmNomeDaPessoaEOHorarioQueMudou() {
        var marcadaPelaPaula = new Disponibilidade(ANA, manha, Resposta.PODE, PAULA);
        var antesDaMudanca = new Disponibilidade(ANA, noite, Resposta.PODE, ANA);
        noite.alterar("Culto de domingo", LocalTime.of(19, 0), DUAS_HORAS);
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(ANA, List.of(500L, 501L, 502L)))
                .thenReturn(List.of(marcadaPelaPaula, antesDaMudanca));

        var linhas = consulta.doMembro(ANA, NOVEMBRO).grupo(MIDIA).orElseThrow().linhas();

        assertThat(linhas.get(0).marcadoPor()).isEqualTo("Paula Ribeiro");
        assertThat(linhas.get(0).aviso()).isNull();
        assertThat(linhas.get(1).marcadoPor()).isNull();
        assertThat(linhas.get(1).aviso()).isEqualTo("O horário mudou (era 18h00). Toque de novo para confirmar.");
        assertThat(linhas.get(1).pode()).as("a resposta continua").isTrue();
    }

    @Test
    void avulsoQueMudouDeDataAvisaComADataAntiga() {
        var avulso = evento(novembroDaMidia, 503L, "Conferência", 21, LocalTime.of(15, 0));
        var resposta = new Disponibilidade(ANA, avulso, Resposta.NAO_PODE, ANA);
        avulso.mudarData(LocalDate.of(2026, 11, 22), novembroDaMidia);
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO)).thenReturn(List.of(avulso));
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(ANA, List.of(503L))).thenReturn(List.of(resposta));
        when(disponibilidades.findByUsuarioIdAndEventoIdIn(ANA, List.of(600L))).thenReturn(List.of());

        assertThat(consulta.doMembro(ANA, NOVEMBRO).grupo(MIDIA).orElseThrow().linhas())
                .singleElement()
                .satisfies(linha -> {
                    assertThat(linha.naoPode()).isTrue();
                    assertThat(linha.aviso())
                            .isEqualTo("A data mudou (era 21/11 · Sáb, 15h00). Toque de novo para confirmar.");
                });
    }

    @Test
    void semMinisterioNemEventoNaoConsultaAsRespostas() {
        when(membros.ministeriosEmQueServe(PAULA)).thenReturn(List.of());
        when(eventos.porVirDoMes(LOUVOR, NOVEMBRO)).thenReturn(List.of());
        when(membros.ministeriosEmQueServe(ANA)).thenReturn(List.of(Exemplos.louvor()));

        var semMinisterio = consulta.doMembro(PAULA, NOVEMBRO);
        var semEvento = consulta.doMembro(ANA, NOVEMBRO);

        assertThat(semMinisterio.serveEmAlgum()).isFalse();
        assertThat(semMinisterio.grupos()).isEmpty();
        assertThat(semMinisterio.titulo()).isEqualTo("Disponibilidade — Novembro");
        assertThat(semEvento.titulo()).isEqualTo("Louvor — Novembro");
        assertThat(semEvento.contagem()).isEqualTo("0 de 0 respondidos");
        verify(disponibilidades, never()).findByUsuarioIdAndEventoIdIn(anyLong(), any());
    }

    @Test
    void painelPoeQuemFaltaPrimeiroEContaQuemPodeEmCadaEvento() {
        when(membros.queServem(MIDIA)).thenReturn(List.of(ANA_SOUZA, BRUNO, CARLA));
        when(disponibilidades.findByEventoIdIn(List.of(500L, 501L, 502L)))
                .thenReturn(List.of(
                        new Disponibilidade(ANA, manha, Resposta.PODE, ANA),
                        new Disponibilidade(ANA, noite, Resposta.NAO_PODE, ANA),
                        new Disponibilidade(ANA, quinta, Resposta.PODE, PAULA),
                        new Disponibilidade(31L, noite, Resposta.PODE, 31L),
                        new Disponibilidade(99L, manha, Resposta.PODE, 99L)));

        var painel = consulta.painel(MIDIA, NOVEMBRO);

        assertThat(painel.temPeriodo()).isTrue();
        assertThat(painel.travado()).isFalse();
        assertThat(painel.eventos())
                .extracting(EventoDoPainel::dia, EventoDoPainel::horario, EventoDoPainel::podem)
                .as("quem deixou de servir (99) não conta")
                .containsExactly(
                        tuple("01 Dom", "09h30", 1L), tuple("01 Dom", "18h00", 1L), tuple("05 Qui", "19h30", 1L));
        assertThat(painel.membros())
                .extracting(
                        linha -> linha.pessoa().nome(),
                        LinhaDoPainel::estado,
                        LinhaDoPainel::varianteDoEstado,
                        LinhaDoPainel::resumo)
                .containsExactly(
                        tuple("Bruno Lima", "Faltam 2", "vazio", "Respondeu 1 de 3"),
                        tuple("Carla Dias", "Sem resposta", "alerta", "Sem resposta"),
                        tuple("Ana Souza", "Respondeu", null, "Pode em 2 de 3"));
        assertThat(painel.membros().get(0).respostas()).containsExactly(null, Resposta.PODE, null);
        assertThat(painel.faltamResponder()).hasSize(2);
        assertThat(painel.responderam()).hasSize(1);
        assertThat(painel.semNenhumaResposta()).isEqualTo(1);
        assertThat(painel.nomeDoMes()).isEqualTo("novembro");
    }

    @Test
    void eventoNovoVoltaTodoMundoParaPendente() {
        when(membros.queServem(MIDIA)).thenReturn(List.of(ANA_SOUZA));
        var novo = evento(novembroDaMidia, 503L, "Culto de ação de graças", 26, LocalTime.of(20, 0));
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO)).thenReturn(List.of(manha, novo));
        when(disponibilidades.findByEventoIdIn(List.of(500L, 503L)))
                .thenReturn(List.of(new Disponibilidade(ANA, manha, Resposta.PODE, ANA)));

        assertThat(consulta.painel(MIDIA, NOVEMBRO).membros())
                .singleElement()
                .extracting(LinhaDoPainel::estado)
                .isEqualTo("Falta 1");
    }

    @Test
    void mesSemEventosNaoTemPeriodoNemConsultaRespostas() {
        when(periodos.doMes(MIDIA, NOVEMBRO)).thenReturn(Optional.empty());
        when(eventos.porVirDoMes(MIDIA, NOVEMBRO)).thenReturn(List.of());
        when(membros.queServem(MIDIA)).thenReturn(List.of(ANA_SOUZA));

        var painel = consulta.painel(MIDIA, NOVEMBRO);

        assertThat(painel.temPeriodo()).isFalse();
        assertThat(painel.membros()).singleElement().satisfies(linha -> {
            assertThat(linha.respondeuTudo())
                    .as("sem eventos ninguém respondeu tudo")
                    .isFalse();
            assertThat(linha.estado()).isEqualTo("Sem resposta");
        });
        verify(disponibilidades, never()).findByEventoIdIn(any());
    }

    @Test
    void quemPodeSoTemQuemServeEMarcouPode() {
        when(membros.queServem(MIDIA)).thenReturn(List.of(ANA_SOUZA, BRUNO, CARLA));
        when(disponibilidades.findByEventoIdIn(List.of(500L, 501L, 502L)))
                .thenReturn(List.of(
                        new Disponibilidade(ANA, manha, Resposta.PODE, ANA),
                        new Disponibilidade(31L, manha, Resposta.NAO_PODE, 31L),
                        new Disponibilidade(31L, noite, Resposta.PREFERE_NAO, 31L),
                        new Disponibilidade(99L, noite, Resposta.PODE, 99L)));

        assertThat(consulta.quemPode(MIDIA, NOVEMBRO))
                .as("sem resposta, Não pode e quem não serve (desativado ou sem habilitação) ficam de fora")
                .containsExactlyInAnyOrderEntriesOf(Map.of(500L, Set.of(ANA), 501L, Set.of(), 502L, Set.of()));
    }

    private static Evento evento(Periodo periodo, Long id, String nome, int dia, LocalTime horario) {
        return ExemplosDeEvento.comId(
                Evento.avulso(periodo, nome, periodo.getMes().atDay(dia), horario, DUAS_HORAS), id);
    }
}
