package br.igreja.escala.escala.domain;

/**
 * Os parâmetros de um tipo de regra, gravados em JSON na coluna {@code regra.parametros}. Cada tipo tem o seu record,
 * que valida os valores ao ser criado, inclusive quando vem do banco.
 */
public sealed interface ParametrosDeRegra permits SemParametros, LimitePorPeriodoParams, MaxPorNivelParams {}
