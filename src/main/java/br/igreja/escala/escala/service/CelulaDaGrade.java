package br.igreja.escala.escala.service;

import java.util.List;

/** A célula de uma função num evento: as vagas, ou "não precisa" se o evento não exige a função. */
public record CelulaDaGrade(String funcao, boolean exigida, List<SlotDaGrade> vagas) {}
