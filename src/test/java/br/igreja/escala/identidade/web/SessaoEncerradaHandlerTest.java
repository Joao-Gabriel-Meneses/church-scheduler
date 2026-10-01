package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.Pessoas;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.web.session.SessionInformationExpiredEvent;

class SessaoEncerradaHandlerTest {

    private final SessaoEncerradaHandler handler = new SessaoEncerradaHandler();
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();

    @Test
    void levaAoLoginComOAviso() throws Exception {
        handler.onExpiredSessionDetected(evento(new MockHttpServletRequest("GET", "/")));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/login?expirou");
    }

    @Test
    void noHtmxTrocaAPaginaInteiraConsiderandoOContexto() throws Exception {
        var requisicao = new MockHttpServletRequest("GET", "/escala/ministerios/1/membros");
        requisicao.setContextPath("/escala");
        requisicao.addHeader("HX-Request", "true");

        handler.onExpiredSessionDetected(evento(requisicao));

        assertThat(resposta.getHeader("HX-Redirect")).isEqualTo("/escala/login?expirou");
        assertThat(resposta.getRedirectedUrl()).isNull();
    }

    private SessionInformationExpiredEvent evento(MockHttpServletRequest requisicao) {
        var sessao = new SessionInformation(Pessoas.membro(1L, "Ana"), "sessao", new Date());
        sessao.expireNow();
        return new SessionInformationExpiredEvent(sessao, requisicao, resposta);
    }
}
