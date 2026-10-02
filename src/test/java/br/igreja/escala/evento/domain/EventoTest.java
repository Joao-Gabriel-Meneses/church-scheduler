package br.igreja.escala.evento.domain;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import br.igreja.escala.evento.ExemplosDeEvento;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventoTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);
    private final ModeloEvento cultoDeDomingo = ExemplosDeEvento.cultoDeDomingo(1L);

    @Test
    void eventoDoModeloLevaNomeEHorarioPadrao() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        assertThat(evento.getNome()).isEqualTo("Culto de domingo");
        assertThat(evento.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(evento.getMinisterioId()).isEqualTo(1L);
        assertThat(evento.getPeriodo()).isSameAs(outubro);
        assertThat(evento.isAvulso()).isFalse();
        assertThat(evento.isCancelado()).isFalse();
    }

    @Test
    void eventoDoModeloSoNoDiaDaSemanaDeleNoMesDoPeriodoENoMesmoMinisterio() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(cultoDeDomingo, outubro, LocalDate.of(2026, 10, 5)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(cultoDeDomingo, outubro, LocalDate.of(2026, 11, 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(ExemplosDeEvento.cultoDeDomingo(2L), outubro, DOMINGO));
    }

    @Test
    void mudarOHorarioDoEventoNaoMudaOModelo() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.alterar("Culto de domingo especial", LocalTime.of(19, 0, 15), DUAS_HORAS);

        assertThat(evento.getHorario()).isEqualTo(LocalTime.of(19, 0));
        assertThat(cultoDeDomingo.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(cultoDeDomingo.getNome()).isEqualTo("Culto de domingo");
    }

    @Test
    void avulsoMudaDeDataEDePeriodoMasODoModeloNao() {
        var conferencia = Evento.avulso(
                outubro, "Conferência de jovens", LocalDate.of(2026, 10, 17), LocalTime.of(15, 0), DUAS_HORAS);
        var novembro = ExemplosDeEvento.periodo(1L, YearMonth.of(2026, 11));

        conferencia.mudarData(LocalDate.of(2026, 11, 7), novembro);

        assertThat(conferencia.isAvulso()).isTrue();
        assertThat(conferencia.getPeriodo()).isSameAs(novembro);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> conferencia.mudarData(LocalDate.of(2026, 12, 5), novembro));
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> Evento.doModelo(cultoDeDomingo, outubro, DOMINGO).mudarData(DOMINGO, outubro));
    }

    @Test
    void cancelaEReativa() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.cancelar();
        assertThat(evento.isCancelado()).isTrue();
        evento.reativar();
        assertThat(evento.isCancelado()).isFalse();
    }

    @Test
    void avulsoExigeNomeDataEHorario() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.avulso(outubro, " ", DOMINGO, LocalTime.NOON, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.avulso(outubro, "Ensaio", null, LocalTime.NOON, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.avulso(outubro, "Ensaio", DOMINGO, null, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.avulso(null, "Ensaio", DOMINGO, LocalTime.NOON, DUAS_HORAS));
    }

    @Test
    void eventoDoModeloLevaADuracaoDoModeloETerminaNoFimDela() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        assertThat(evento.getDuracao()).isEqualTo(DUAS_HORAS);
        assertThat(evento.getInicio()).isEqualTo(DOMINGO.atTime(18, 0));
        assertThat(evento.getFim()).isEqualTo(DOMINGO.atTime(20, 0));
    }

    @Test
    void eventoQueTerminaDepoisDaMeiaNoiteTerminaNoDiaSeguinte() {
        var vigilia = Evento.avulso(outubro, "Vigília", LocalDate.of(2026, 10, 30), LocalTime.of(23, 0), DUAS_HORAS);

        assertThat(vigilia.getFim()).isEqualTo(LocalDate.of(2026, 10, 31).atTime(1, 0));
    }

    @Test
    void mudarADuracaoDoEventoNaoMudaADoModelo() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.alterar("Culto de domingo", LocalTime.of(18, 0), Duration.ofMinutes(90));

        assertThat(evento.getFim()).isEqualTo(DOMINGO.atTime(19, 30));
        assertThat(cultoDeDomingo.getDuracao()).isEqualTo(DUAS_HORAS);
    }

    @Test
    void duracaoVaiDe15MinutosAUmDiaEmMinutosInteiros() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.alterar("Culto", LocalTime.NOON, Duration.ofMinutes(15));
        evento.alterar("Culto", LocalTime.NOON, Duration.ofHours(24));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evento.alterar("Culto", LocalTime.NOON, Duration.ofMinutes(14)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evento.alterar("Culto", LocalTime.NOON, Duration.ofMinutes(24 * 60 + 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evento.alterar("Culto", LocalTime.NOON, Duration.ofSeconds(90 * 60 + 30)));
        assertThatIllegalArgumentException().isThrownBy(() -> evento.alterar("Culto", LocalTime.NOON, null));
    }

    @Test
    void semEscolhaOEventoPrecisaDeTodasAsFuncoes() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        assertThat(evento.getFuncoesExigidas()).isEmpty();
        assertThat(evento.exige(100L)).isTrue();
        assertThat(evento.exige(101L)).isTrue();
    }

    @Test
    void eventoComFuncoesEscolhidasSoPrecisaDelasEVazioVoltaATodas() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.exigirFuncoes(List.of(100L));
        assertThat(evento.exige(100L)).isTrue();
        assertThat(evento.exige(101L)).isFalse();

        evento.exigirFuncoes(Set.of());
        assertThat(evento.exige(101L)).isTrue();
    }

    @Test
    void eventoDoModeloCopiaAsFuncoesDoModeloSemFicarPresoAElas() {
        cultoDeDomingo.exigirFuncoes(Set.of(100L));
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        cultoDeDomingo.exigirFuncoes(Set.of(101L));

        assertThat(evento.getFuncoesExigidas()).containsExactly(100L);
    }
}
