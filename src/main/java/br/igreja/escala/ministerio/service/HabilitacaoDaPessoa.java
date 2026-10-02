package br.igreja.escala.ministerio.service;

/** Uma habilitação, só com os ids: a geração da escala monta o nível de cada pessoa em cada função. */
public record HabilitacaoDaPessoa(Long usuarioId, Long funcaoId, Long nivelId) {}
