package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

class SenhaProvisoriaInterceptorTest {

    private final SenhaProvisoriaInterceptor interceptor = new SenhaProvisoriaInterceptor();
    private final MockHttpServletResponse resposta = new MockHttpServletResponse();

    @AfterEach
    void limpaASessao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void comSenhaProvisoriaQualquerPaginaLevaATrocaDeSenha() throws Exception {
        logado(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));

        assertThat(interceptor.preHandle(requisicao("/ministerios/1/membros"), resposta, controller()))
                .isFalse();
        assertThat(resposta.getRedirectedUrl()).isEqualTo("/conta/senha");
    }

    @Test
    void noHtmxRedirecionaAPaginaInteira() throws Exception {
        logado(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));
        var requisicao = requisicao("/");
        requisicao.addHeader("HX-Request", "true");

        assertThat(interceptor.preHandle(requisicao, resposta, controller())).isFalse();
        assertThat(resposta.getHeader("HX-Redirect")).isEqualTo("/conta/senha");
        assertThat(resposta.getRedirectedUrl()).isNull();
    }

    @Test
    void deixaPassarATrocaDeSenhaAPaginaDeErroEOsArquivosEstaticos() throws Exception {
        logado(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));

        assertThat(interceptor.preHandle(requisicao("/conta/senha"), resposta, controller()))
                .isTrue();
        assertThat(interceptor.preHandle(requisicao("/error"), resposta, controller()))
                .isTrue();
        assertThat(interceptor.preHandle(requisicao("/css/app.css"), resposta, new Object()))
                .isTrue();
    }

    @Test
    void semSenhaProvisoriaOuSemLoginNaoInterfere() throws Exception {
        assertThat(interceptor.preHandle(requisicao("/"), resposta, controller()))
                .isTrue();

        logado(Usuario.membro("Ana", "ana@x.com", "hash"));
        assertThat(interceptor.preHandle(requisicao("/"), resposta, controller()))
                .isTrue();
        assertThat(resposta.getRedirectedUrl()).isNull();
    }

    @Test
    void consideraOContextoDaAplicacao() throws Exception {
        logado(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));
        var requisicao = requisicao("/escala/");
        requisicao.setContextPath("/escala");

        interceptor.preHandle(requisicao, resposta, controller());

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/escala/conta/senha");
    }

    private static void logado(Usuario usuario) {
        var autenticado = new UsuarioAutenticado(usuario);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        autenticado, null, autenticado.getAuthorities()));
    }

    private static MockHttpServletRequest requisicao(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static HandlerMethod controller() throws NoSuchMethodException {
        Method metodo = Object.class.getMethod("toString");
        return new HandlerMethod(new Object(), metodo);
    }
}
