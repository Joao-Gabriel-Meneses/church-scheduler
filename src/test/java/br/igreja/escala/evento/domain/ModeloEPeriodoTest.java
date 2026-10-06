package br.igreja.escala.evento.domain;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ModeloEPeriodoTest {

    @Test
    void modeloNasceAtivoComHorarioSemSegundos() {
        var modelo = new ModeloEvento(1L, " Culto de domingo ", DayOfWeek.SUNDAY, LocalTime.of(18, 0, 30), DUAS_HORAS);

        assertThat(modelo.getNome()).isEqualTo("Culto de domingo");
        assertThat(modelo.isAtivo()).isTrue();
        assertThat(modelo.getHorario()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void modeloMudaEDesativa() {
        var modelo = new ModeloEvento(1L, "Culto de quinta", DayOfWeek.THURSDAY, LocalTime.of(19, 30), DUAS_HORAS);

        modelo.alterar("Culto de quarta", DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), DUAS_HORAS, false);

        assertThat(modelo.getDiaDaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(modelo.isAtivo()).isFalse();
    }

    @Test
    void modeloExigeMinisterioNomeDiaEHorario() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModeloEvento(null, "Culto", DayOfWeek.SUNDAY, LocalTime.NOON, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModeloEvento(1L, " ", DayOfWeek.SUNDAY, LocalTime.NOON, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModeloEvento(1L, "Culto", null, LocalTime.NOON, DUAS_HORAS));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ModeloEvento(1L, "Culto", DayOfWeek.SUNDAY, null, DUAS_HORAS));
    }

    @Test
    void periodoEUmMesDoMinisterioEmRascunhoEAberto() {
        var outubro = new Periodo(1L, YearMonth.of(2026, 10));

        assertThat(outubro.getMes()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(outubro.isDisponibilidadeTravada()).isFalse();
        assertThat(outubro.getStatusDaEscala()).isEqualTo(StatusDaEscala.RASCUNHO);
        assertThat(outubro.contem(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(outubro.contem(LocalDate.of(2026, 11, 1))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new Periodo(null, YearMonth.of(2026, 10)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Periodo(1L, null));
    }

    @Test
    void periodoTravaEDestravaADisponibilidadeUmaVez() {
        var outubro = new Periodo(1L, YearMonth.of(2026, 10));

        assertThat(outubro.travarDisponibilidade()).isTrue();
        assertThat(outubro.isDisponibilidadeTravada()).isTrue();
        assertThat(outubro.travarDisponibilidade()).isFalse();
        assertThat(outubro.destravarDisponibilidade()).isTrue();
        assertThat(outubro.isDisponibilidadeTravada()).isFalse();
        assertThat(outubro.destravarDisponibilidade()).isFalse();
    }

    @Test
    void diaDaSemanaGravaONumeroIso() {
        var conversor = new DiaDaSemanaConverter();

        assertThat(conversor.convertToDatabaseColumn(DayOfWeek.SUNDAY)).isEqualTo(7);
        assertThat(conversor.convertToEntityAttribute(4)).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(conversor.convertToDatabaseColumn(null)).isNull();
        assertThat(conversor.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void escalaNasceRascunhoPublicaEReabre() {
        var outubro = new Periodo(1L, YearMonth.of(2026, 10));

        assertThat(outubro.isEscalaPublicada()).isFalse();
        assertThat(outubro.reabrirEscala()).isFalse();
        assertThat(outubro.publicarEscala()).isTrue();
        assertThat(outubro.getStatusDaEscala()).isEqualTo(StatusDaEscala.PUBLICADA);
        assertThat(outubro.publicarEscala()).isFalse();
        assertThat(outubro.reabrirEscala()).isTrue();
        assertThat(outubro.getStatusDaEscala()).isEqualTo(StatusDaEscala.RASCUNHO);
    }
}
