package br.igreja.escala.identidade.service;

/**
 * Senha que não pode ser usada. A mensagem vai para o usuário, no campo indicado.
 *
 * @see UsuarioService#trocarSenha(Long, String, String)
 */
public class SenhaRecusadaException extends RuntimeException {

    private final String campo;

    public SenhaRecusadaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    /** Campo do formulário em que o erro aparece. */
    public String campo() {
        return campo;
    }
}
