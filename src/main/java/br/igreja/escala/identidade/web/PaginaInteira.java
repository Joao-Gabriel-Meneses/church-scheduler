package br.igreja.escala.identidade.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Redirecionamento que troca a página inteira, também numa requisição do htmx. */
final class PaginaInteira {

    private PaginaInteira() {}

    /**
     * @param caminho caminho da aplicação, sem o context path
     */
    static void redirecionar(HttpServletRequest requisicao, HttpServletResponse resposta, String caminho)
            throws IOException {
        String destino = requisicao.getContextPath() + caminho;
        // Numa requisição do htmx, um 302 só trocaria o fragmento; o HX-Redirect leva a página inteira.
        if ("true".equals(requisicao.getHeader("HX-Request"))) {
            resposta.setHeader("HX-Redirect", destino);
        } else {
            resposta.sendRedirect(destino);
        }
    }
}
