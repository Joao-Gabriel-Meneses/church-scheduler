package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.service.EscaladosDoEvento.NaFuncao;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Outubro na Mídia: Projeção (1 vaga), Som (1 a 2 vagas) e Apoio (0 a 1). No dia 11, o culto da noite foi criado antes
 * do da manhã; no dia 18, um culto cancelado; no dia 24, um casamento só com Projeção.
 */
class EscaladosDoEventoTest {

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, YearMonth.of(2026, 10));
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao som = Exemplos.comId(new Funcao(Exemplos.midia(), "Som", Icone.MIC, 1, 2), 102L);
    private final Funcao apoio = Exemplos.comId(new Funcao(Exemplos.midia(), "Apoio", Icone.USERS, 0, 1), 103L);

    private final Evento noite = evento(501L, "Culto da noite", 11, LocalTime.of(18, 0));
    private final Evento manha = evento(502L, "Culto da manhã", 11, LocalTime.of(9, 30));
    private final Evento cancelado = evento(503L, "Culto", 18, LocalTime.of(18, 0));
    private final Evento casamento = evento(504L, "Casamento", 24, LocalTime.of(16, 0));

    private final UsuarioResumo ana = pessoa(30L, "Ana Souza");
    private final UsuarioResumo bruno = pessoa(31L, "Bruno Alves");
    private final UsuarioResumo davi = pessoa(33L, "Davi Rocha");

    EscaladosDoEventoTest() {
        cancelado.cancelar();
        casamento.exigirFuncoes(Set.of(projecao.getId()));
    }

    @Test
    void eventosNaoCanceladosEmOrdemDeInicioMesmoNoMesmoDia() {
        var doMes = EscaladosDoEvento.doMes(dados(List.of()));

        assertThat(doMes)
                .extracting(EscaladosDoEvento::quando, EscaladosDoEvento::nome)
                .containsExactly(
                        tuple("11/10 · Dom · 09h30", "Culto da manhã"),
                        tuple("11/10 · Dom · 18h00", "Culto da noite"),
                        tuple("24/10 · Sáb · 16h00", "Casamento"));
    }

    @Test
    void cadaFuncaoTemOsNomesPorPosicaoEADefinirNaObrigatoriaVazia() {
        var vagas = List.of(
                vaga(1L, noite, projecao, 1, ana.id()),
                vaga(2L, noite, som, 1, null),
                vaga(3L, noite, som, 2, bruno.id()),
                vaga(4L, noite, apoio, 1, null),
                vaga(5L, manha, projecao, 1, bruno.id()),
                vaga(6L, manha, som, 1, davi.id()),
                vaga(7L, manha, som, 2, null),
                vaga(8L, manha, apoio, 1, ana.id()),
                vaga(9L, casamento, projecao, 1, null));

        var doMes = EscaladosDoEvento.doMes(dados(vagas));

        assertThat(doMes.get(0).funcoes())
                .as("manhã: a 2ª vaga de Som é opcional e não aparece")
                .containsExactly(
                        new NaFuncao("Projeção", true, List.of("Bruno Alves")),
                        new NaFuncao("Som", true, List.of("Davi Rocha")),
                        new NaFuncao("Apoio", true, List.of("Ana Souza")));
        assertThat(doMes.get(1).funcoes())
                .as("noite: obrigatória vazia é a definir; Apoio sem ninguém também")
                .containsExactly(
                        new NaFuncao("Projeção", true, List.of("Ana Souza")),
                        new NaFuncao("Som", true, List.of(EscaladosDoEvento.A_DEFINIR, "Bruno Alves")),
                        new NaFuncao("Apoio", true, List.of(EscaladosDoEvento.A_DEFINIR)));
        assertThat(doMes.get(2).funcoes())
                .containsExactly(
                        new NaFuncao("Projeção", true, List.of(EscaladosDoEvento.A_DEFINIR)),
                        new NaFuncao("Som", false, List.of()),
                        new NaFuncao("Apoio", false, List.of()));
    }

    @Test
    void desistenciaEAjusteDepoisDaPublicacaoAparecemComoAEscalaEstaAgora() {
        var desistida = vaga(1L, noite, projecao, 1, ana.id());
        desistida.desistir(Instant.parse("2026-10-05T17:32:00Z"));
        var ajustada = vaga(5L, manha, projecao, 1, bruno.id());
        ajustada.ajustar(davi.id());

        var doMes = EscaladosDoEvento.doMes(dados(List.of(desistida, ajustada)));

        assertThat(doMes.get(0).funcoes().getFirst().pessoas()).containsExactly("Davi Rocha");
        assertThat(doMes.get(1).funcoes().getFirst().pessoas()).containsExactly(EscaladosDoEvento.A_DEFINIR);
    }

    private DadosDoPeriodo dados(List<Vaga> vagas) {
        return new DadosDoPeriodo(
                1L,
                "Mídia",
                YearMonth.of(2026, 10),
                outubro,
                List.of(noite, manha, cancelado, casamento),
                List.of(projecao, som, apoio),
                vagas,
                List.of(ana, bruno),
                List.of(davi),
                Map.of(),
                List.of(),
                Map.of(),
                RegrasDoMinisterio.padrao(),
                List.of(),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                List.of());
    }

    private Evento evento(Long id, String nome, int dia, LocalTime horario) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, nome, LocalDate.of(2026, 10, dia), horario, DUAS_HORAS), id);
    }

    private static Vaga vaga(Long id, Evento evento, Funcao funcao, int posicao, Long usuarioId) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), posicao), id);
        vaga.escalar(usuarioId);
        return vaga;
    }

    private static UsuarioResumo pessoa(Long id, String nome) {
        return new UsuarioResumo(id, nome, id + "@teste.local", null, false, false, true);
    }
}
