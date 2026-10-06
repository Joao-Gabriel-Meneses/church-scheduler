package br.igreja.escala.compartilhado.web;

import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.LinhaDeDisponibilidade;
import br.igreja.escala.escala.service.CelulaDaGrade;
import br.igreja.escala.escala.service.LinhaDaGrade;
import br.igreja.escala.escala.service.SlotDaGrade;
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
        model.addAttribute("abas", abas("Escalas"));
        model.addAttribute(
                "niveis", List.of(new Opcao("INICIANTE", "Iniciante"), new Opcao("EXPERIENTE", "Experiente")));
        model.addAttribute("disponibilidade", disponibilidade());
        model.addAttribute("funcoesDaGrade", List.of("Projeção", "Transmissão"));
        model.addAttribute("linhasDaGrade", grade());
        return "dev/componentes";
    }

    /** As linhas do preview do ScheduleGrid: preenchida, fixada, vazia e forçada. */
    private static List<LinhaDaGrade> grade() {
        return List.of(
                linha(
                        "05",
                        "Dom",
                        "Culto de domingo",
                        "18h00",
                        vaga("Ana Souza", "Experiente"),
                        vaga("Lucas Lima", "Iniciante")),
                linha(
                        "09",
                        "Qui",
                        "Culto de quinta",
                        "19h30",
                        SlotDaGrade.de(null, 0, "Pedro Alves", "Experiente", true, false, null, null, false),
                        vaga("Bia Rocha", "Experiente")),
                linha(
                        "12",
                        "Dom",
                        "Culto de domingo",
                        "18h00",
                        vaga("João Meneses", "Experiente"),
                        SlotDaGrade.vazia(null, 0, true, false, false)),
                linha(
                        "16",
                        "Qui",
                        "Culto de quinta",
                        "19h30",
                        SlotDaGrade.de(
                                null,
                                0,
                                "Carla Dias",
                                "Iniciante",
                                true,
                                true,
                                "Única que opera a mesa nova",
                                "LIMITE_POR_PERIODO",
                                false),
                        vaga("Ana Souza", "Experiente")),
                linha(
                        "18",
                        "Sáb",
                        "Conferência de jovens",
                        "15h00",
                        vaga("Lucas Lima", "Iniciante"),
                        vaga("Pedro Alves", "Experiente")));
    }

    private static LinhaDaGrade linha(
            String dia,
            String diaDaSemana,
            String nome,
            String horario,
            SlotDaGrade projecao,
            SlotDaGrade transmissao) {
        return new LinhaDaGrade(
                dia,
                diaDaSemana,
                nome,
                horario,
                projecao.vazia() || transmissao.vazia(),
                List.of(
                        new CelulaDaGrade("Projeção", true, List.of(projecao)),
                        new CelulaDaGrade("Transmissão", true, List.of(transmissao))));
    }

    private static SlotDaGrade vaga(String nome, String nivel) {
        return SlotDaGrade.de(null, 0, nome, nivel, false, false, null, null, false);
    }

    /** As linhas do preview do AvailabilityPicker, mais uma marcada pelo gerente e outra de evento que mudou. */
    private static List<LinhaDeDisponibilidade> disponibilidade() {
        return List.of(
                linha(1L, "05", "Dom", "Culto de domingo", "18h00", Resposta.PODE, null, null),
                linha(2L, "09", "Qui", "Culto de quinta", "19h30", Resposta.NAO_PODE, null, null),
                linha(3L, "12", "Dom", "Culto de domingo", "18h00", null, null, null),
                linha(4L, "16", "Qui", "Culto de quinta", "19h30", Resposta.PODE, "Paula Ribeiro", null),
                linha(
                        5L,
                        "18",
                        "Sáb",
                        "Conferência de jovens",
                        "15h00",
                        Resposta.PODE,
                        null,
                        "O horário mudou (era 14h00). Toque de novo para confirmar."));
    }

    private static LinhaDeDisponibilidade linha(
            Long id,
            String dia,
            String diaDaSemana,
            String nome,
            String horario,
            Resposta resposta,
            String marcadoPor,
            String aviso) {
        return new LinhaDeDisponibilidade(
                id, nome, dia, diaDaSemana, dia + "/10 · " + diaDaSemana, horario, resposta, marcadoPor, aviso);
    }

    @GetMapping("/membro")
    String membro(Model model) {
        model.addAttribute("navegacao", abas(null));
        model.addAttribute("ministerios", ministerios());
        model.addAttribute("sucesso", "Disponibilidade de outubro salva");
        return "dev/layout-membro";
    }

    /** Página de escalas: a única com "Gerar escala" como primário na Toolbar. */
    @GetMapping("/gerente")
    String gerenteEscalas(Model model) {
        model.addAttribute("navegacao", abas("Escalas"));
        model.addAttribute("ministerios", ministerios());
        model.addAttribute("funcoesDaGrade", List.of("Projeção", "Transmissão"));
        model.addAttribute("linhasDaGrade", grade());
        return "dev/gerente-escalas";
    }

    /** Página de membros: a Toolbar só tem período e trava; o primário é "Convidar membro". */
    @GetMapping("/gerente/membros")
    String gerenteMembros(Model model) {
        model.addAttribute("navegacao", abas("Membros"));
        model.addAttribute("ministerios", ministerios());
        return "dev/gerente-membros";
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

    /**
     * NavPills (README do NavPills): Início, Disponibilidade e o menu Gerenciar, iguais em toda página. Com
     * {@code atual}, o item do menu com esse rótulo é a página atual.
     */
    static List<ItemDeNavegacao> abas(String atual) {
        String gerente = "/dev/componentes/gerente";
        return List.of(
                new ItemDeNavegacao("Início", "Início", "house", "/dev/componentes/membro", atual == null),
                new ItemDeNavegacao("Disponibilidade", "Disponib.", "list-checks", "/dev/componentes/membro#", false),
                ItemDeNavegacao.menu(
                        "Gerenciar",
                        "Gerenciar",
                        "layout-dashboard",
                        List.of(
                                filho("Escalas", "layout-dashboard", gerente, atual, false),
                                filho("Eventos", "calendar", gerente + "#", atual, false),
                                filho("Disponibilidade", "list-checks", gerente + "#", atual, false),
                                filho("Membros", "users", gerente + "/membros", atual, false),
                                filho("Funções", "layers", gerente + "#", atual, false),
                                filho("Regras", "settings-2", gerente + "#", atual, false),
                                filho("Ministérios", "church", gerente + "#", atual, true))));
    }

    private static ItemDeNavegacao filho(String rotulo, String icone, String url, String atual, boolean separado) {
        return new ItemDeNavegacao(rotulo, rotulo, icone, url, rotulo.equals(atual), separado, List.of());
    }
}
