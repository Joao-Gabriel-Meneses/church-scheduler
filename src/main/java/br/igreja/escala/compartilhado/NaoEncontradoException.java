package br.igreja.escala.compartilhado;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Registro que não existe ou não pertence ao ministério da rota. As duas situações respondem 404 de propósito: quem
 * tenta um id de outro ministério não descobre que ele existe.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
