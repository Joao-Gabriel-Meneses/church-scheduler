package br.igreja.escala.compartilhado.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Põe a navegação principal (${navegacao}) e a SideRail (${ministerios}) no model de toda página; os layouts desenham
 * a partir delas.
 */
@ControllerAdvice
class NavegacaoAdvice {

    /** Ausente nos testes de controller, que não carregam o módulo ministerio: a navegação fica sem ministérios. */
    private final ObjectProvider<MinisteriosGerenciados> gerenciados;

    NavegacaoAdvice(ObjectProvider<MinisteriosGerenciados> gerenciados) {
        this.gerenciados = gerenciados;
    }

    @ModelAttribute
    void navegacao(Authentication autenticacao, HttpServletRequest requisicao, Model model) {
        // Sem login (ou anônimo) não há navegação.
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken) {
            model.addAttribute("navegacao", List.of());
            return;
        }
        String caminho =
                requisicao.getRequestURI().substring(requisicao.getContextPath().length());
        var doUsuario = ministeriosDe(autenticacao);
        model.addAttribute("navegacao", Navegacao.PRINCIPAL.itens(autenticacao.getAuthorities(), caminho, doUsuario));
        model.addAttribute("ministerios", Navegacao.ministerios(caminho, doUsuario));
    }

    private List<MinisterioNaNavegacao> ministeriosDe(Authentication autenticacao) {
        var fonte = gerenciados.getIfAvailable();
        return fonte == null ? List.of() : fonte.de(autenticacao);
    }
}
