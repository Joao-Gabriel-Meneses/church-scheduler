package br.igreja.escala.compartilhado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Duration;

/** Duração gravada em minutos inteiros, num {@code NUMBER(4)}. */
@Converter
public class DuracaoEmMinutos implements AttributeConverter<Duration, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Duration duracao) {
        return duracao == null ? null : Math.toIntExact(duracao.toMinutes());
    }

    @Override
    public Duration convertToEntityAttribute(Integer minutos) {
        return minutos == null ? null : Duration.ofMinutes(minutos);
    }
}
