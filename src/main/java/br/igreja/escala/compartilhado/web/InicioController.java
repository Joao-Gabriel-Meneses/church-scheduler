package br.igreja.escala.compartilhado.web;

import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** O início: as escalas da pessoa e a disponibilidade do próximo mês na mesma tela, sem aba própria. */
@Controller
class InicioController {

    /** Ausente nos testes de controller, que não carregam o módulo disponibilidade: o resumo fica vazio. */
    private final ObjectProvider<DisponibilidadeNoInicio> disponibilidade;

    InicioController(ObjectProvider<DisponibilidadeNoInicio> disponibilidade) {
        this.disponibilidade = disponibilidade;
    }

    @GetMapping("/")
    String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        var fonte = disponibilidade.getIfAvailable();
        model.addAttribute(
                "disponibilidade", fonte == null ? ResumoDaDisponibilidade.vazio() : fonte.doMembro(usuario.getId()));
        return "inicio";
    }
}
