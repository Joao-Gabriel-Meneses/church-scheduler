package br.igreja.escala.escala.service;

/**
 * Uma regra na página de regras. {@code edicao} é o trecho da rota do formulário ("limite", "minimo-por-nivel"), ou
 * nulo quando a regra não se edita.
 */
public record RegraResumo(
        String codigo, String nome, String descricao, String rigidez, String estado, boolean ligada, String edicao) {}
