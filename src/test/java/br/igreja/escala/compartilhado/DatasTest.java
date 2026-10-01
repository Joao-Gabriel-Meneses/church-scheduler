package br.igreja.escala.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DatasTest {

    @Test
    void dataCurtaComDiaDaSemanaSemPonto() {
        assertThat(Datas.dataCurta(LocalDate.of(2026, 10, 4))).isEqualTo("04/10 · Dom");
        assertThat(Datas.dataCurta(LocalDate.of(2026, 10, 10))).isEqualTo("10/10 · Sáb");
        assertThat(Datas.dia(LocalDate.of(2026, 10, 4))).isEqualTo("04");
        assertThat(Datas.diaEMes(LocalDate.of(2026, 1, 9))).isEqualTo("09/01");
    }

    @Test
    void horarioComH() {
        assertThat(Datas.horario(LocalTime.of(18, 0))).isEqualTo("18h00");
        assertThat(Datas.horario(LocalTime.of(9, 30))).isEqualTo("09h30");
        assertThat(Datas.faixaDeHorario(LocalTime.of(18, 0), Duration.ofMinutes(90)))
                .isEqualTo("18h00 às 19h30");
        assertThat(Datas.faixaDeHorario(LocalTime.of(23, 0), Duration.ofHours(2)))
                .isEqualTo("23h00 às 01h00");
    }

    @Test
    void diasDaSemanaPorExtensoECurtos() {
        assertThat(Datas.diaDaSemana(DayOfWeek.SUNDAY)).isEqualTo("Domingo");
        assertThat(Datas.diaDaSemana(DayOfWeek.MONDAY)).isEqualTo("Segunda");
        assertThat(Datas.diaDaSemanaCurto(DayOfWeek.THURSDAY)).isEqualTo("Qui");
    }

    @Test
    void mesPorExtensoECurto() {
        assertThat(Datas.mesPorExtenso(YearMonth.of(2026, 10))).isEqualTo("Outubro 2026");
        assertThat(Datas.mesCurto(YearMonth.of(2026, 3))).isEqualTo("Mar 2026");
        assertThat(Datas.nomeDoMes(YearMonth.of(2027, 1))).isEqualTo("Janeiro");
    }
}
