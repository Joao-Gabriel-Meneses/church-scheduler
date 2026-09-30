package br.igreja.escala.identidade.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * Depois de e-mail ou senha errados, volta ao login com o e-mail que a pessoa digitou; a senha nunca volta. O e-mail vai
 * num flash attribute (sessão), não na URL, para não ficar no histórico do navegador nem nos logs.
 */
public class FalhaDeLoginHandler extends SimpleUrlAuthenticationFailureHandler {

    static final String URL_DE_ERRO = "/login?erro";

    private final FlashMapManager flashMaps = new SessionFlashMapManager();

    public FalhaDeLoginHandler() {
        super(URL_DE_ERRO);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest requisicao, HttpServletResponse resposta, AuthenticationException excecao)
            throws IOException, ServletException {
        String email = requisicao.getParameter("email");
        if (email != null && !email.isBlank()) {
            var flash = new FlashMap();
            flash.put("email", email.strip());
            flash.setTargetRequestPath(requisicao.getContextPath() + "/login");
            flashMaps.saveOutputFlashMap(flash, requisicao, resposta);
        }
        super.onAuthenticationFailure(requisicao, resposta, excecao);
    }
}
