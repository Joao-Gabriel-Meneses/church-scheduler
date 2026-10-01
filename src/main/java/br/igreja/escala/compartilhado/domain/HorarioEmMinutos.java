package br.igreja.escala.compartilhado.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalTime;

/**
 * Horário da igreja (sem fuso nem data) gravado em minutos desde a meia-noite, num {@code NUMBER(4)}: o Oracle não tem
 * tipo só de hora, e um TIMESTAMP traria uma data falsa junto.
 */
@Converter
public class HorarioEmMinutos implements AttributeConverter<LocalTime, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LocalTime horario) {
        return horario == null ? null : horario.getHour() * 60 + horario.getMinute();
    }

    @Override
    public LocalTime convertToEntityAttribute(Integer minutos) {
        return minutos == null ? null : LocalTime.of(minutos / 60, minutos % 60);
    }
}
