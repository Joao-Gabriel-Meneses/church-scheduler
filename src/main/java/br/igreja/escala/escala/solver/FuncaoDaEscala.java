package br.igreja.escala.escala.solver;

/** Uma função do ministério: até {@code qtdMin} as posições são obrigatórias; até {@code qtdMax}, opcionais. */
public record FuncaoDaEscala(Long id, String nome, int qtdMin, int qtdMax) {}
