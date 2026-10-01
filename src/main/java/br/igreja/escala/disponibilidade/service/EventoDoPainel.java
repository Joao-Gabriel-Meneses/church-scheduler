package br.igreja.escala.disponibilidade.service;

/**
 * Uma coluna da visão membro × evento.
 *
 * @param dia "01 Dom"
 * @param horario "18h00"
 * @param podem quantos dos que servem marcaram Pode
 */
public record EventoDoPainel(Long id, String nome, String dia, String horario, long podem) {}
