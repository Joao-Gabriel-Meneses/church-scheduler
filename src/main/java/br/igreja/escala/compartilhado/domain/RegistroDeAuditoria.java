package br.igreja.escala.compartilhado.domain;

/**
 * O que registrar na auditoria.
 *
 * @param acao o que foi feito
 * @param autorId quem fez (gerente ou admin)
 * @param ministerioId onde, se a ação é de um ministério
 * @param alvoUsuarioId sobre quem, se a ação é sobre uma pessoa
 * @param descricao frase em português com os nomes, para ler sem cruzar tabelas ("Ana Souza virou gerente da Mídia")
 */
public record RegistroDeAuditoria(
        AcaoAuditada acao, Long autorId, Long ministerioId, Long alvoUsuarioId, String descricao) {}
