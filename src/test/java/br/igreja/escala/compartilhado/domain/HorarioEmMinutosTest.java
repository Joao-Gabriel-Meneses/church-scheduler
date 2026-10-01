package br.igreja.escala.compartilhado.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class HorarioEmMinutosTest {

    private final HorarioEmMinutos conversor = new HorarioEmMinutos();

    @Test
    void gravaMinutosDesdeAMeiaNoiteELeDeVolta() {
        assertThat(conversor.convertToDatabaseColumn(LocalTime.of(18, 0))).isEqualTo(1080);
        assertThat(conversor.convertToDatabaseColumn(LocalTime.of(23, 59))).isEqualTo(1439);
        assertThat(conversor.convertToEntityAttribute(1170)).isEqualTo(LocalTime.of(19, 30));
        assertThat(conversor.convertToEntityAttribute(0)).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void nuloContinuaNulo() {
        assertThat(conversor.convertToDatabaseColumn(null)).isNull();
        assertThat(conversor.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void duracaoTambemViraMinutos() {
        var duracao = new DuracaoEmMinutos();

        assertThat(duracao.convertToDatabaseColumn(Duration.ofMinutes(90))).isEqualTo(90);
        assertThat(duracao.convertToEntityAttribute(120)).isEqualTo(Duration.ofHours(2));
        assertThat(duracao.convertToDatabaseColumn(null)).isNull();
        assertThat(duracao.convertToEntityAttribute(null)).isNull();
    }
}
