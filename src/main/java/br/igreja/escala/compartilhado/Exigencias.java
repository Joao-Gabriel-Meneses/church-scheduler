package br.igreja.escala.compartilhado;

/**
 * Invariantes das entidades. A validação que o usuário vê fica nos formulários (Bean Validation); estas exceções só
 * disparam quando um valor inválido escapa dela, então a mensagem é para quem programa.
 */
public final class Exigencias {

    private Exigencias() {}

    /** Texto obrigatório, sem os espaços das pontas, com no máximo {@code tamanhoMaximo} caracteres. */
    public static String texto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        String limpo = valor.strip();
        if (limpo.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(campo + " tem mais de " + tamanhoMaximo + " caracteres");
        }
        return limpo;
    }

    public static <T> T presente(T valor, String campo) {
        if (valor == null) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor;
    }

    public static int entre(int valor, int minimo, int maximo, String campo) {
        if (valor < minimo || valor > maximo) {
            throw new IllegalArgumentException(campo + " precisa estar entre " + minimo + " e " + maximo);
        }
        return valor;
    }
}
