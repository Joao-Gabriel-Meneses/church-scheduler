package br.igreja.escala.escala.domain;

/**
 * Uma regra como vale para a geração: sem JPA, para ir para o solver e para as telas. {@code ativa} já considera o que
 * a deixa sem efeito (ex.: o nível do máximo por nível foi excluído).
 */
public record RegraVigente(TipoDeRegra tipo, Rigidez rigidez, int peso, boolean ativa, ParametrosDeRegra parametros) {

    public <T extends ParametrosDeRegra> T parametros(Class<T> classe) {
        return classe.cast(parametros);
    }
}
