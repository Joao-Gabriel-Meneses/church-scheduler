package br.igreja.escala.compartilhado;

/**
 * O registro mudou depois que a tela o leu (outra aba, ou outra pessoa): a alteração é recusada em vez de sobrescrever
 * o que foi gravado. A tela relê o registro e avisa.
 */
public class EdicaoConcorrenteException extends RuntimeException {

    public EdicaoConcorrenteException(String mensagem) {
        super(mensagem);
    }
}
