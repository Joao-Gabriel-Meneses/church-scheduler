package br.igreja.escala.compartilhado.web;

/**
 * As escalas publicadas de quem está logado, resumidas para o início ("Minhas escalas"). Implementada pelo módulo
 * escala; a interface fica aqui para o início não depender dele.
 */
public interface EscalasNoInicio {

    MinhasEscalas doMembro(Long usuarioId);
}
