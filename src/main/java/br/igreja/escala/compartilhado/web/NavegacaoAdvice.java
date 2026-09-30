package br.igreja.escala.compartilhado.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Põe a navegação principal (${navegacao}) no model de toda página; os layouts desenham a partir dela. */
@ControllerAdvice
class NavegacaoAdvice {

    @ModelAttribute("navegacao")
    List<ItemDeNavegacao> navegacao(Authentication autenticacao, HttpServletRequest requisicao) {
        // Sem login (ou anônimo, que não tem perfil de membro) não há navegação.
        if (autenticacao == null) {
            return List.of();
        }
        String caminho =
                requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        return Navegacao.PRINCIPAL.itens(autenticacao.getAuthorities(), caminho);
    }
}
