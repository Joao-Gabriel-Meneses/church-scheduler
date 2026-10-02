package br.igreja.escala.escala.service;

import java.util.List;

/** Os números da escala do mês e as escalas de cada pessoa, da maior carga para a menor. */
public record ResumoDaEscala(
        int vagas, int preenchidas, int vazias, int eventos, long maiorCarga, List<CargaDaPessoa> porPessoa) {}
