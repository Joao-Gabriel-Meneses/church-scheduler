package br.igreja.escala.escala.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.EdicaoConcorrenteException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.service.AjusteDaEscala;
import br.igreja.escala.escala.service.ConsultaDaEscala;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import jakarta.servlet.http.HttpServletResponse;
import java.time.YearMonth;
import java.util.function.Supplier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * O ajuste manual de uma vaga pelo gerente. Com htmx, a vaga abre no Sheet da página de escalas e cada ação devolve a
 * região #escala inteira (contagens, avisos e grade) com o toast; a recusa volta para o Sheet, com a mensagem no campo,
 * e a vaga que mudou em outra aba recarrega a região com o aviso. Sem JS, a vaga é uma página e as ações redirecionam.
 */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/escalas/vagas/{vagaId}")
class VagaController {

    static final String PAGINA = "escala/vaga";
    static final String CONTEUDO = "escala/fragments/vaga :: conteudo";
    static final String REGIAO = "escala/fragments/escala :: regiao";
    static final String MUDOU_EM_OUTRA_ABA = "A vaga mudou em outra aba ou por outra pessoa depois que você abriu a"
            + " escala. A grade foi atualizada: confira e tente de novo.";

    private final AjusteDaEscala ajuste;
    private final ConsultaDaEscala consulta;
    private final MinisterioService ministerios;

    VagaController(AjusteDaEscala ajuste, ConsultaDaEscala consulta, MinisterioService ministerios) {
        this.ajuste = ajuste;
        this.consulta = consulta;
        this.ministerios = ministerios;
    }

    @GetMapping
    String abrir(
            @PathVariable Long ministerioId,
            @PathVariable Long vagaId,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            Model model) {
        preencherVaga(ministerioId, vagaId, "true".equals(htmx), model);
        return "true".equals(htmx) ? CONTEUDO : PAGINA;
    }

    @PostMapping("/escalar")
    String escalar(
            @PathVariable Long ministerioId,
            @PathVariable Long vagaId,
            @RequestParam Long usuarioId,
            @RequestParam(required = false) String justificativa,
            @RequestParam long versao,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        return responder(
                () -> ajuste.escalar(ministerioId, vagaId, usuarioId, justificativa, versao, autor),
                new Pedido(ministerioId, vagaId, mes, "true".equals(htmx), usuarioId, justificativa),
                model,
                resposta,
                redirecionamento);
    }

    @PostMapping("/esvaziar")
    String esvaziar(
            @PathVariable Long ministerioId,
            @PathVariable Long vagaId,
            @RequestParam long versao,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        return responder(
                () -> ajuste.esvaziar(ministerioId, vagaId, versao, autor),
                new Pedido(ministerioId, vagaId, mes, "true".equals(htmx), null, null),
                model,
                resposta,
                redirecionamento);
    }

    @PostMapping("/fixar")
    String fixar(
            @PathVariable Long ministerioId,
            @PathVariable Long vagaId,
            @RequestParam long versao,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        return responder(
                () -> ajuste.fixar(ministerioId, vagaId, versao, autor),
                new Pedido(ministerioId, vagaId, mes, "true".equals(htmx), null, null),
                model,
                resposta,
                redirecionamento);
    }

    @PostMapping("/desafixar")
    String desafixar(
            @PathVariable Long ministerioId,
            @PathVariable Long vagaId,
            @RequestParam long versao,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        return responder(
                () -> ajuste.desafixar(ministerioId, vagaId, versao, autor),
                new Pedido(ministerioId, vagaId, mes, "true".equals(htmx), null, null),
                model,
                resposta,
                redirecionamento);
    }

    private String responder(
            Supplier<String> acao,
            Pedido pedido,
            Model model,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        try {
            String sucesso = acao.get();
            if (!pedido.htmx()) {
                redirecionamento.addFlashAttribute("sucesso", sucesso);
                return "redirect:" + escalas(pedido);
            }
            preencherRegiao(pedido, model);
            model.addAttribute("toast", sucesso);
            return REGIAO;
        } catch (RegraVioladaException recusa) {
            Long recusaDe = "justificativa".equals(recusa.campo()) ? pedido.usuarioId() : null;
            if (!pedido.htmx()) {
                redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
                redirecionamento.addFlashAttribute("recusaDe", recusaDe);
                redirecionamento.addFlashAttribute("justificativaDigitada", pedido.justificativa());
                return "redirect:/ministerios/" + pedido.ministerioId() + "/escalas/vagas/" + pedido.vagaId();
            }
            preencherVaga(pedido.ministerioId(), pedido.vagaId(), true, model);
            model.addAttribute("recusa", recusa.getMessage());
            model.addAttribute("recusaDe", recusaDe);
            model.addAttribute("justificativaDigitada", pedido.justificativa());
            resposta.setHeader("HX-Retarget", "#vaga-conteudo");
            resposta.setHeader("HX-Reswap", "innerHTML");
            return CONTEUDO;
        } catch (EdicaoConcorrenteException | ObjectOptimisticLockingFailureException concorrente) {
            if (!pedido.htmx()) {
                redirecionamento.addFlashAttribute("recusa", MUDOU_EM_OUTRA_ABA);
                return "redirect:" + escalas(pedido);
            }
            preencherRegiao(pedido, model);
            model.addAttribute("aviso", MUDOU_EM_OUTRA_ABA);
            return REGIAO;
        }
    }

    private void preencherVaga(Long ministerioId, Long vagaId, boolean htmx, Model model) {
        var aberta = ajuste.abrir(ministerioId, vagaId);
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("aberta", aberta);
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(aberta.mes()));
        model.addAttribute("htmx", htmx);
    }

    private void preencherRegiao(Pedido pedido, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(pedido.ministerioId()));
        model.addAttribute("mes", pedido.mes());
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(pedido.mes()));
        model.addAttribute("pagina", consulta.doMes(pedido.ministerioId(), pedido.mes()));
    }

    private static String escalas(Pedido pedido) {
        return "/ministerios/" + pedido.ministerioId() + "/escalas?mes=" + pedido.mes();
    }

    /** O que a resposta precisa saber do pedido. */
    private record Pedido(
            Long ministerioId, Long vagaId, YearMonth mes, boolean htmx, Long usuarioId, String justificativa) {}
}
