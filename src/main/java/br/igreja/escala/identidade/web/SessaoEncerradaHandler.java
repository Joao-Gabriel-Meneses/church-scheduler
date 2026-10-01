package br.igreja.escala.identidade.web;

import java.io.IOException;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

/** Sessão encerrada por SessoesAbertas: o logout já foi feito; a pessoa vai ao login, que explica o motivo. */
public class SessaoEncerradaHandler implements SessionInformationExpiredStrategy {

    static final String URL = "/login?expirou";

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent evento) throws IOException {
        PaginaInteira.redirecionar(evento.getRequest(), evento.getResponse(), URL);
    }
}
