package br.igreja.escala.identidade.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class LoginController {

    @GetMapping("/login")
    String login(Authentication autenticacao, @RequestParam(required = false) String saiu, Model model) {
        boolean logado = autenticacao != null
                && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken);
        if (logado) {
            return "redirect:/";
        }
        // O logout redireciona para /login?saiu; o layout mostra o toast de sucesso.
        if (saiu != null) {
            model.addAttribute("sucesso", "Você saiu da sua conta");
        }
        return "identidade/login";
    }
}
