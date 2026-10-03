package br.igreja.escala.escala.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converte os parâmetros de uma regra de e para o JSON da coluna. Estrito: campo desconhecido, faltando ou nulo onde
 * o record não aceita é erro, para um JSON torto no banco não virar uma regra diferente sem ninguém ver.
 */
final class ParametrosEmJson {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .build();

    private ParametrosEmJson() {}

    /**
     * @throws IllegalArgumentException se o JSON não é um {@code tipo} válido
     */
    static <T extends ParametrosDeRegra> T ler(String json, Class<T> tipo) {
        try {
            return JSON.readValue(json, tipo);
        } catch (JacksonException invalido) {
            throw new IllegalArgumentException(
                    "parâmetros inválidos para " + tipo.getSimpleName() + ": " + json, invalido);
        }
    }

    static String escrever(ParametrosDeRegra parametros) {
        return JSON.writeValueAsString(parametros);
    }
}
