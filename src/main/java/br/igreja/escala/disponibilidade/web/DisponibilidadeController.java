package br.igreja.escala.disponibilidade.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.disponibilidade.service.TelaDaDisponibilidade;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A disponibilidade de quem está logado: os eventos do mês por ministério, marcados um toque por vez. Grava sempre
 * para o usuário da sessão; a rota não recebe o id de ninguém.
 */
@Controller
@RequestMapping("/disponibilidade")
class DisponibilidadeController {

    static final String TELA = "disponibilidade/membro";
    static final String RESPOSTA = "disponibilidade/fragments/grupo :: respostaDoMembro";

    private final ConsultaDaDisponibilidade consulta;
    private final DisponibilidadeService disponibilidades;
    private final EventoService eventos;

    DisponibilidadeController(
            ConsultaDaDisponibilidade consulta, DisponibilidadeService disponibilidades, EventoService eventos) {
        this.consulta = consulta;
        this.disponibilidades = disponibilidades;
        this.eventos = eventos;
    }

    /** Sem {@code mes}, abre o mês seguinte, o mesmo que o gerente prepara. */
    @GetMapping
    String tela(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        model.addAttribute("tela", consulta.doMembro(usuario.getId(), doMes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(doMes));
        model.addAttribute("mesCurto", Datas.mesCurto(doMes));
        model.addAttribute("mesAnterior", doMes.minusMonths(1));
        model.addAttribute("proximoMes", doMes.plusMonths(1));
        return TELA;
    }

    /**
     * Um toque no AvailabilityPicker. Com htmx, devolve o grupo do ministério já atualizado (travado, se o gerente
     * travou no meio do caminho) e o total; sem JS, volta para a tela.
     */
    @PostMapping("/ministerios/{ministerioId}/eventos/{eventoId}")
    String marcar(
            @PathVariable Long ministerioId,
            @PathVariable Long eventoId,
            @RequestParam Resposta resposta,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model,
            RedirectAttributes redirecionamento) {
        String recusa = null;
        try {
            disponibilidades.marcar(usuario.getId(), ministerioId, eventoId, resposta);
        } catch (RegraVioladaException regra) {
            recusa = regra.getMessage();
        }
        if (!"true".equals(htmx)) {
            if (recusa != null) {
                redirecionamento.addFlashAttribute("recusa", recusa);
                redirecionamento.addFlashAttribute("recusaDoGrupo", ministerioId);
            }
            return "redirect:/disponibilidade?mes=" + mes + "#grupo-" + ministerioId;
        }
        TelaDaDisponibilidade tela = consulta.doMembro(usuario.getId(), mes);
        model.addAttribute("tela", tela);
        model.addAttribute(
                "grupo",
                tela.grupo(ministerioId)
                        .orElseThrow(() -> new NaoEncontradoException("Ministério " + ministerioId + " da pessoa")));
        model.addAttribute("recusa", recusa);
        return RESPOSTA;
    }
}
