package br.igreja.escala.escala.service;

/** Um AlertBanner da página de escalas: o quê e onde (título), por quê (corpo) e a regra, se houver. */
public record AlertaDaEscala(String titulo, String corpo, String regra) {}
