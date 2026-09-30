package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.support.SessionFlashMapManager;

class FalhaDeLoginHandlerTest {

    private final FalhaDeLoginHandler handler = new FalhaDeLoginHandler();

    @Test
    void voltaAoLoginComOEmailDigitadoForaDaUrl() throws Exception {
        var tentativa = new MockHttpServletRequest("POST", "/login");
        tentativa.addParameter("email", "  Ana@Exemplo.com ");
        tentativa.addParameter("senha", "errada");
        var resposta = new MockHttpServletResponse();

        handler.onAuthenticationFailure(tentativa, resposta, new BadCredentialsException("senha errada"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/login?erro");
        assertThat(flashDoLogin(tentativa))
                .containsEntry("email", "Ana@Exemplo.com")
                .doesNotContainKey("senha");
    }

    @Test
    void semEmailNaoGuardaNada() throws Exception {
        var tentativa = new MockHttpServletRequest("POST", "/login");
        var resposta = new MockHttpServletResponse();

        handler.onAuthenticationFailure(tentativa, resposta, new BadCredentialsException("sem e-mail"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/login?erro");
        assertThat(flashDoLogin(tentativa)).isNull();
    }

    /** O que o GET /login seguinte, na mesma sessão, recebe como flash attributes. */
    private static Map<String, Object> flashDoLogin(MockHttpServletRequest tentativa) {
        var volta = new MockHttpServletRequest("GET", "/login");
        volta.setQueryString("erro");
        volta.setSession(tentativa.getSession());
        return new SessionFlashMapManager().retrieveAndUpdate(volta, new MockHttpServletResponse());
    }
}
