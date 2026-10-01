package br.igreja.escala.ministerio.service;

import br.igreja.escala.identidade.service.UsuarioResumo;

/**
 * @param jaTinhaConta o e-mail já tinha conta: a pessoa só entrou no ministério, com a senha que já tinha
 */
public record ResultadoDoCadastro(UsuarioResumo membro, boolean jaTinhaConta) {}
