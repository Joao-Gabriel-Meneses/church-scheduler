package br.igreja.escala.identidade.web;

import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enquanto a senha for provisória, qualquer página leva à troca de senha. Arquivos estáticos e a página de erro
 * passam; o logout é um filtro do Spring Security e nem chega aqui.
 */
public class SenhaProvisoriaInterceptor implements HandlerInterceptor {

    public static final String TROCA_DE_SENHA = "/conta/senha";

    @Override
    public boolean preHandle(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod) || !temSenhaProvisoria()) {
            return true;
        }
        String caminho =
                requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        if (caminho.equals(TROCA_DE_SENHA) || caminho.equals("/error")) {
            return true;
        }
        String destino = requisicao.getContextPath() + TROCA_DE_SENHA;
        // Numa requisição do htmx, um 302 só trocaria o fragmento; o HX-Redirect leva a página inteira.
        if ("true".equals(requisicao.getHeader("HX-Request"))) {
            resposta.setHeader("HX-Redirect", destino);
        } else {
            resposta.sendRedirect(destino);
        }
        return false;
    }

    private static boolean temSenhaProvisoria() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.isSenhaProvisoria();
    }
}
