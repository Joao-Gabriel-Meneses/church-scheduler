package br.igreja.escala.compartilhado.web;

/**
 * Ministério que o usuário gerencia, como a navegação precisa dele.
 *
 * @param icone nome do ícone Lucide (src/main/frontend/icones.json)
 */
public record MinisterioNaNavegacao(Long id, String nome, String icone) {}
