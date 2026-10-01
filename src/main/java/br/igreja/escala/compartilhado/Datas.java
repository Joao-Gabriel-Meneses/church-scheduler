package br.igreja.escala.compartilhado;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Datas e horários como o design escreve (docs/design/README.md, "Conteúdo e voz"): "12/10 · Dom", "18h00",
 * "Outubro 2026". Os nomes ficam aqui, e não nos dados de locale da JVM, para não variar entre versões (o pt-BR do
 * Java escreve "dom." com ponto).
 */
public final class Datas {

    private static final String[] MESES = {
        "Janeiro",
        "Fevereiro",
        "Março",
        "Abril",
        "Maio",
        "Junho",
        "Julho",
        "Agosto",
        "Setembro",
        "Outubro",
        "Novembro",
        "Dezembro"
    };
    private static final String[] DIAS = {"Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};
    private static final String[] DIAS_CURTOS = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};

    private static final DateTimeFormatter DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter HORARIO = DateTimeFormatter.ofPattern("HH'h'mm");

    private Datas() {}

    /** "12/10 · Dom". */
    public static String dataCurta(LocalDate data) {
        return data.format(DIA_E_MES) + " · " + diaDaSemanaCurto(data.getDayOfWeek());
    }

    /** "05", o dia com dois dígitos (AvailabilityPicker, ScheduleGrid). */
    public static String dia(LocalDate data) {
        return data.format(DIA);
    }

    /** "18h00". */
    public static String horario(LocalTime horario) {
        return horario.format(HORARIO);
    }

    /** "Domingo". */
    public static String diaDaSemana(DayOfWeek dia) {
        return DIAS[dia.getValue() - 1];
    }

    /** "Dom". */
    public static String diaDaSemanaCurto(DayOfWeek dia) {
        return DIAS_CURTOS[dia.getValue() - 1];
    }

    /** "Outubro". */
    public static String nomeDoMes(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1];
    }

    /** "Outubro 2026". */
    public static String mesPorExtenso(YearMonth mes) {
        return nomeDoMes(mes) + " " + mes.getYear();
    }

    /** "Out 2026", para o seletor de período compacto do celular. */
    public static String mesCurto(YearMonth mes) {
        return nomeDoMes(mes).substring(0, 3) + " " + mes.getYear();
    }
}
