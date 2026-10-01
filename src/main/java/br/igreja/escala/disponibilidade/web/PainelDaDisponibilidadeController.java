package br.igreja.escala.disponibilidade.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.disponibilidade.service.LembreteDaDisponibilidade;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Disponibilidade do ministério para o gerente: quem respondeu, a visão membro × evento, a trava do mês e a marcação em
 * nome de um membro (que vale mesmo com o período travado e fica na auditoria).
 */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/disponibilidade")
class PainelDaDisponibilidadeController {

    static final String PAINEL = "disponibilidade/painel";
    static final String MEMBRO = "disponibilidade/membro-pelo-gerente";
    static final String RESPOSTA = "disponibilidade/fragments/grupo :: respostaDoGerente";

    private final ConsultaDaDisponibilidade consulta;
    private final DisponibilidadeService disponibilidades;
    private final LembreteDaDisponibilidade lembretes;
    private final MinisterioService ministerios;
    private final EventoService eventos;

    PainelDaDisponibilidadeController(
            ConsultaDaDisponibilidade consulta,
            DisponibilidadeService disponibilidades,
            LembreteDaDisponibilidade lembretes,
            MinisterioService ministerios,
            EventoService eventos) {
        this.consulta = consulta;
        this.disponibilidades = disponibilidades;
        this.lembretes = lembretes;
        this.ministerios = ministerios;
        this.eventos = eventos;
    }

    /**
     * Sem {@code mes}, abre o mês seguinte, que é o que o gerente prepara. Com a disponibilidade aberta, traz o lembrete
     * pronto para o WhatsApp.
     */
    @GetMapping
    String painel(
            @PathVariable Long ministerioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        var painel = consulta.painel(ministerioId, doMes);
        preencherMes(ministerioId, doMes, model);
        model.addAttribute("painel", painel);
        boolean lembrar = !painel.travado()
                && !painel.eventos().isEmpty()
                && !painel.membros().isEmpty();
        model.addAttribute(
                "lembrete",
                lembrar ? lembretes.texto(ministerios.buscar(ministerioId).getNome(), painel) : null);
        return PAINEL;
    }

    /** As respostas de um membro, para o gerente marcar em nome dele. */
    @GetMapping("/membros/{usuarioId}")
    String membro(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        model.addAttribute("daPessoa", consulta.peloGerente(ministerioId, usuarioId, doMes));
        preencherMes(ministerioId, doMes, model);
        return MEMBRO;
    }

    /** Um toque na página do membro. Com htmx, devolve o grupo atualizado; sem JS, volta para a página. */
    @PostMapping("/membros/{usuarioId}/eventos/{eventoId}")
    String marcar(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @PathVariable Long eventoId,
            @RequestParam Resposta resposta,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            RedirectAttributes redirecionamento) {
        String recusa = null;
        try {
            disponibilidades.marcarPeloGerente(ministerioId, usuarioId, eventoId, resposta, autor);
        } catch (RegraVioladaException regra) {
            recusa = regra.getMessage();
        }
        if (!"true".equals(htmx)) {
            if (recusa != null) {
                redirecionamento.addFlashAttribute("recusa", recusa);
            }
            return "redirect:/ministerios/" + ministerioId + "/disponibilidade/membros/" + usuarioId + "?mes=" + mes;
        }
        model.addAttribute("daPessoa", consulta.peloGerente(ministerioId, usuarioId, mes));
        model.addAttribute("recusa", recusa);
        return RESPOSTA;
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

    private void preencherMes(Long ministerioId, YearMonth mes, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("mes", mes);
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(mes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(mes));
        model.addAttribute("mesCurto", Datas.mesCurto(mes));
        model.addAttribute("mesAnterior", mes.minusMonths(1));
        model.addAttribute("proximoMes", mes.plusMonths(1));
    }

    private static String nomeDoMes(YearMonth mes) {
        return Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
    }

    private static String paraOPainel(Long ministerioId, YearMonth mes) {
        return "redirect:/ministerios/" + ministerioId + "/disponibilidade?mes=" + mes;
    }
}
