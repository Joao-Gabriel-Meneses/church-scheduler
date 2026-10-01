package br.igreja.escala.identidade.web;

import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.AcessoRevogado;
import java.util.stream.Stream;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sessões abertas de cada usuário, no registro do Spring Security (SecurityConfig). Encerrar marca a sessão como
 * expirada: na próxima requisição o {@code ConcurrentSessionFilter} faz o logout, apagando também o cookie do
 * "continuar conectado", e leva ao login (SessaoEncerradaHandler).
 */
public class SessoesAbertas {

    private final SessionRegistry registro;

    public SessoesAbertas(SessionRegistry registro) {
        this.registro = registro;
    }

    /** Depois do commit: se a transação desfaz a mudança, ninguém sai. Fora de transação, na hora. */
    @TransactionalEventListener(fallbackExecution = true)
    public void aoRevogarAcesso(AcessoRevogado evento) {
        encerrarTodas(evento.usuarioId());
    }

    public void encerrarTodas(Long usuarioId) {
        sessoesDe(usuarioId).forEach(SessionInformation::expireNow);
    }

    /** Troca da própria senha: as outras sessões caem e a desta requisição continua. */
    public void encerrarOutras(Long usuarioId, String sessaoAtual) {
        sessoesDe(usuarioId)
                .filter(sessao -> !sessao.getSessionId().equals(sessaoAtual))
                .forEach(SessionInformation::expireNow);
    }

    private Stream<SessionInformation> sessoesDe(Long usuarioId) {
        return registro.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UsuarioAutenticado usuario
                        && usuario.getId().equals(usuarioId))
                .flatMap(principal -> registro.getAllSessions(principal, false).stream());
    }
}
