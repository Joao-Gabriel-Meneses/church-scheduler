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

    /** Ausentes nos testes de controller, que não carregam os módulos disponibilidade e escala: o resumo fica vazio. */
    private final ObjectProvider<DisponibilidadeNoInicio> disponibilidade;

    private final ObjectProvider<EscalasNoInicio> escalas;

    InicioController(ObjectProvider<DisponibilidadeNoInicio> disponibilidade, ObjectProvider<EscalasNoInicio> escalas) {
        this.disponibilidade = disponibilidade;
        this.escalas = escalas;
    }

    @GetMapping("/")
    String inicio(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        var fonte = disponibilidade.getIfAvailable();
        model.addAttribute(
                "disponibilidade", fonte == null ? ResumoDaDisponibilidade.vazio() : fonte.doMembro(usuario.getId()));
        var minhas = escalas.getIfAvailable();
        model.addAttribute("minhasEscalas", minhas == null ? MinhasEscalas.vazio() : minhas.doMembro(usuario.getId()));
        return "inicio";
    }
}
