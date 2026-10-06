package br.igreja.escala.escala.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.service.DesistenciaDaEscala;
import br.igreja.escala.escala.service.EscalaDoMembro;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A escala do ministério para o membro: a grade publicada do mês, só leitura, para quem é membro do ministério (o admin
 * vê todos). Rascunho não aparece, nem por URL direta; ministério de que a pessoa não é membro responde 404.
 *
 * <p>E a desistência, que o membro confirma em "Minhas escalas", no início: vale sempre para quem está logado (a rota
 * não recebe id de pessoa), e a vaga de outra pessoa responde 404.
 */
@Controller
class EscalaDoMembroController {

    static final String PAGINA = "escala/do-ministerio";

    private final EscalaDoMembro escalas;
    private final DesistenciaDaEscala desistencias;
    private final Clock relogio;

    EscalaDoMembroController(EscalaDoMembro escalas, DesistenciaDaEscala desistencias, Clock relogio) {
        this.escalas = escalas;
        this.desistencias = desistencias;
        this.relogio = relogio;
    }

    /** Sem {@code mes}, abre o mês de hoje. */
    @GetMapping("/escalas/{ministerioId}")
    String escala(
            @PathVariable Long ministerioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            Model model) {
        var doMes = mes == null ? YearMonth.now(relogio) : mes;
        model.addAttribute("escala", escalas.doMinisterio(ministerioId, doMes, usuario));
        model.addAttribute("outrosMinisterios", escalas.ministerios(usuario));
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(doMes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(doMes));
        model.addAttribute("mesCurto", Datas.mesCurto(doMes));
        model.addAttribute("mesAnterior", doMes.minusMonths(1));
        model.addAttribute("proximoMes", doMes.plusMonths(1));
        return PAGINA;
    }

    /** Volta ao início: o toast com a vaga que ficou vazia, ou o alerta se passou do prazo. */
    @PostMapping("/escalas/vagas/{vagaId}/desistir")
    String desistir(
            @PathVariable Long vagaId,
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            RedirectAttributes redirecionamento) {
        try {
            redirecionamento.addFlashAttribute("sucesso", desistencias.desistir(vagaId, usuario));
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return "redirect:/";
    }
}
