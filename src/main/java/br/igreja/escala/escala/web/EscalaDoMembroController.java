package br.igreja.escala.escala.web;

import br.igreja.escala.compartilhado.Datas;
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
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A escala do ministério para o membro: a grade publicada do mês, só leitura, para quem é membro do ministério (o admin
 * vê todos). Rascunho não aparece, nem por URL direta; ministério de que a pessoa não é membro responde 404.
 */
@Controller
class EscalaDoMembroController {

    static final String PAGINA = "escala/do-ministerio";

    private final EscalaDoMembro escalas;
    private final Clock relogio;

    EscalaDoMembroController(EscalaDoMembro escalas, Clock relogio) {
        this.escalas = escalas;
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
}
