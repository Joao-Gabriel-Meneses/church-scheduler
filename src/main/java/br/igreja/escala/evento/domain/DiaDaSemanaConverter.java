package br.igreja.escala.evento.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/** Dia da semana como o número ISO (1 = segunda, 7 = domingo), para não depender da ordem do enum. */
@Converter
public class DiaDaSemanaConverter implements AttributeConverter<DayOfWeek, Integer> {

    @Override
    public Integer convertToDatabaseColumn(DayOfWeek dia) {
        return dia == null ? null : dia.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Integer numero) {
        return numero == null ? null : DayOfWeek.of(numero);
    }
}
