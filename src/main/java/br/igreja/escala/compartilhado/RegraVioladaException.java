package br.igreja.escala.compartilhado;

/**
 * Pedido válido no formato mas recusado por uma regra de negócio ("Já existe um ministério chamado Mídia."). A mensagem
 * vai para o usuário: no campo indicado ou, sem campo, num AlertBanner no topo da tela.
 */
public class RegraVioladaException extends RuntimeException {

    private final String campo;

    public RegraVioladaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    /** Sem campo: a regra não é de um campo só. */
    public static RegraVioladaException geral(String mensagem) {
        return new RegraVioladaException(null, mensagem);
    }

    /** Campo do formulário em que o erro aparece, ou nulo. */
    public String campo() {
        return campo;
    }
}
