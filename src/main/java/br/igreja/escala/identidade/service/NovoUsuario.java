package br.igreja.escala.identidade.service;

/**
 * Conta nova de um membro cadastrado pelo gerente.
 *
 * @param telefone opcional
 */
public record NovoUsuario(String nome, String email, String telefone, String senhaProvisoria) {}
