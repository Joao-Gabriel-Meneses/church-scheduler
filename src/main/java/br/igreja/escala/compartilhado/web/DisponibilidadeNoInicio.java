package br.igreja.escala.compartilhado.web;

/**
 * A disponibilidade do próximo mês de quem está logado, resumida para o início. Implementada pelo módulo
 * disponibilidade; a interface fica aqui para o início não depender dele.
 */
public interface DisponibilidadeNoInicio {

    ResumoDaDisponibilidade doMembro(Long usuarioId);
}
