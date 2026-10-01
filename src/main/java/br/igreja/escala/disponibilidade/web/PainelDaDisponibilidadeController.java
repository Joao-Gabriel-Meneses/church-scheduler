package br.igreja.escala.disponibilidade.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Disponibilidade do ministério para o gerente: quem respondeu, a visão membro × evento e a trava do mês. */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/disponibilidade")
class PainelDaDisponibilidadeController {

    static final String PAINEL = "disponibilidade/painel";

    private final ConsultaDaDisponibilidade consulta;
    private final DisponibilidadeService disponibilidades;
    private final MinisterioService ministerios;
    private final EventoService eventos;

    PainelDaDisponibilidadeController(
            ConsultaDaDisponibilidade consulta,
            DisponibilidadeService disponibilidades,
            MinisterioService ministerios,
            EventoService eventos) {
        this.consulta = consulta;
        this.disponibilidades = disponibilidades;
        this.ministerios = ministerios;
        this.eventos = eventos;
    }

    /** Sem {@code mes}, abre o mês seguinte, que é o que o gerente prepara. */
    @GetMapping
    String painel(
            @PathVariable Long ministerioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("painel", consulta.painel(ministerioId, doMes));
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(doMes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(doMes));
        model.addAttribute("mesCurto", Datas.mesCurto(doMes));
        model.addAttribute("mesAnterior", doMes.minusMonths(1));
        model.addAttribute("proximoMes", doMes.plusMonths(1));
        return PAINEL;
    }

    @PostMapping("/travar")
    String travar(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        String doMes = nomeDoMes(mes);
        try {
            redirecionamento.addFlashAttribute(
                    "sucesso",
                    disponibilidades.travar(ministerioId, mes, autor)
                            ? "Disponibilidade de " + doMes + " travada"
                            : "A disponibilidade de " + doMes + " já estava travada");
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return paraOPainel(ministerioId, mes);
    }

    @PostMapping("/destravar")
    String destravar(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        String doMes = nomeDoMes(mes);
        try {
            redirecionamento.addFlashAttribute(
                    "sucesso",
                    disponibilidades.destravar(ministerioId, mes, autor)
                            ? "Disponibilidade de " + doMes + " destravada"
                            : "A disponibilidade de " + doMes + " já estava aberta");
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return paraOPainel(ministerioId, mes);
    }

    private static String nomeDoMes(YearMonth mes) {
        return Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
    }

    private static String paraOPainel(Long ministerioId, YearMonth mes) {
        return "redirect:/ministerios/" + ministerioId + "/disponibilidade?mes=" + mes;
    }
}
