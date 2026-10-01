package br.igreja.escala.identidade.service;

/**
 * Outra pessoa mudou o acesso desta conta (senha redefinida, conta desativada): as sessões abertas dela são encerradas
 * depois do commit (SessoesAbertas).
 */
public record AcessoRevogado(Long usuarioId) {}
