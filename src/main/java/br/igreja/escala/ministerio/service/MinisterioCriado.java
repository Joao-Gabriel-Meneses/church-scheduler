package br.igreja.escala.ministerio.service;

/**
 * Um ministério acabou de ser criado: os outros módulos preparam o que ele precisa (a escala grava o catálogo padrão de
 * regras), na mesma transação.
 */
public record MinisterioCriado(Long ministerioId) {}
