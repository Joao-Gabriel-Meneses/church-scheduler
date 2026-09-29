package br.igreja.escala.compartilhado.web;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vitrine dos fragmentos (templates/componentes) e dos layouts, só no perfil dev, para comparar com os previews de
 * docs/design/components. Os textos e os dados repetem os dos previews.
 */
@Controller
@Profile("dev")
@RequestMapping("/dev/componentes")
class ComponentesDevController {

    @GetMapping
    String componentes(Model model) {
        model.addAttribute("ministerios", ministerios());
        model.addAttribute("abas", abasDoGerente("Escalas"));
        model.addAttribute(
                "niveis", List.of(new Opcao("INICIANTE", "Iniciante"), new Opcao("EXPERIENTE", "Experiente")));
        return "dev/componentes";
    }

    @GetMapping("/membro")
    String membro(Model model) {
        model.addAttribute(
                "navegacao",
                List.of(
                        new ItemDeNavegacao(
                                "Minhas escalas", "Escalas", "calendar-check", "/dev/componentes/membro", true),
                        new ItemDeNavegacao(
                                "Disponibilidade", "Disponibilidade", "calendar", "/dev/componentes/membro#", false)));
        model.addAttribute("ministerios", ministerios());
        model.addAttribute("sucesso", "Disponibilidade de outubro salva");
        return "dev/layout-membro";
    }

    @GetMapping("/gerente")
    String gerente(Model model) {
        model.addAttribute("navegacao", abasDoGerente("Membros"));
        model.addAttribute("ministerios", ministerios());
        return "dev/layout-gerente";
    }

    private static List<ItemDeNavegacao> ministerios() {
        return List.of(
                new ItemDeNavegacao("Mídia", "Mídia", "monitor", "/dev/componentes/gerente", true),
                new ItemDeNavegacao("Louvor", "Louvor", "users", "/dev/componentes/gerente#louvor", false),
                new ItemDeNavegacao(
                        "Recepção", "Recepção", "clipboard-list", "/dev/componentes/gerente#recepcao", false),
                new ItemDeNavegacao(
                        "Relatórios", "Relatórios", "chart-pie", "/dev/componentes/gerente#relatorios", false));
    }

    /** NavPills do gerente (README do NavPills): Escalas, Disponibilidade, Membros e Regras. */
    private static List<ItemDeNavegacao> abasDoGerente(String atual) {
        return List.of(
                new ItemDeNavegacao(
                        "Escalas", "Escalas", "layout-dashboard", "/dev/componentes/gerente#", "Escalas".equals(atual)),
                new ItemDeNavegacao(
                        "Disponibilidade",
                        "Disponibilidade",
                        "calendar",
                        "/dev/componentes/gerente#",
                        "Disponibilidade".equals(atual)),
                new ItemDeNavegacao("Membros", "Membros", "users", "/dev/componentes/gerente", "Membros".equals(atual)),
                new ItemDeNavegacao(
                        "Regras", "Regras", "settings-2", "/dev/componentes/gerente#", "Regras".equals(atual)));
    }
}
