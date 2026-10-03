package br.igreja.escala.escala.service;

import java.util.List;

/**
 * Um evento na grade: dia ("04"), dia da semana ("Dom"), nome, horário ("18h00") e uma célula por função.
 *
 * @param comVagaVazia evento por vir com vaga obrigatória vazia (a linha ganha o filete de alerta)
 */
public record LinhaDaGrade(
        String dia,
        String diaDaSemana,
        String nome,
        String horario,
        boolean comVagaVazia,
        List<CelulaDaGrade> celulas) {}
