package br.igreja.escala.escala.solver;

import br.igreja.escala.escala.domain.TipoDeRegra;

/**
 * Por que uma vaga ficou vazia: a regra que deixou a vaga sem ninguém e a frase para o gerente. Sem regra, ainda havia
 * quem coubesse e a geração não achou a tempo.
 */
public record MotivoDaVagaVazia(TipoDeRegra regra, String texto) {}
