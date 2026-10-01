package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.identidade.service.UsuarioResumo;

/** A disponibilidade de uma pessoa num ministério, como o gerente vê para marcar em nome dela. */
public record DisponibilidadeDeUmMembro(UsuarioResumo pessoa, GrupoDeDisponibilidade grupo) {}
