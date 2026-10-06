package br.igreja.escala.escala.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.compartilhado.web.Opcao;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.service.RegraService;
import br.igreja.escala.escala.web.dto.DadosDoLimite;
import br.igreja.escala.escala.web.dto.DadosDoMinimoPorNivel;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * As regras da escala do ministério: a lista do catálogo, com o que cada uma faz, e os formulários das que o gerente
 * muda nesta fase (o limite do mês e o mínimo por nível). Cada mudança fica na auditoria.
 */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/regras")
class RegraController {

    static final String LISTA = "escala/regras";
    static final String LIMITE = "escala/regra-limite";
    static final String MINIMO_POR_NIVEL = "escala/regra-minimo-por-nivel";

    private final RegraService regras;
    private final MinisterioService ministerios;
    private final NivelService niveis;

    RegraController(RegraService regras, MinisterioService ministerios, NivelService niveis) {
        this.regras = regras;
        this.ministerios = ministerios;
        this.niveis = niveis;
    }

    @GetMapping
    String lista(@PathVariable Long ministerioId, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("regras", regras.resumos(ministerioId));
        return LISTA;
    }

    @GetMapping("/limite")
    String limite(@PathVariable Long ministerioId, Model model) {
        var atual = regras.doMinisterio(ministerioId).limitePorMes();
        return formularioDoLimite(ministerioId, new DadosDoLimite(atual), model);
    }

    @PostMapping("/limite")
    String alterarLimite(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoLimite form,
            BindingResult erros,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                redirecionamento.addFlashAttribute(
                        "sucesso",
                        regras.alterarLimite(ministerioId, form.maximo(), autor)
                                ? "Limite do mês salvo: " + form.maximo() + " por pessoa"
                                : "O limite do mês já era " + form.maximo());
                return paraALista(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formularioDoLimite(ministerioId, form, model);
    }

    @GetMapping("/minimo-por-nivel")
    String minimoPorNivel(@PathVariable Long ministerioId, Model model) {
        var regra = regras.doMinisterio(ministerioId).de(TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
        var parametros = regra.parametros(MinPorNivelParams.class);
        return formularioDoMinimo(
                ministerioId,
                new DadosDoMinimoPorNivel(
                        regra.ativa(), regra.ativa() ? parametros.nivelId() : null, parametros.minimo()),
                model);
    }

    @PostMapping("/minimo-por-nivel")
    String alterarMinimoPorNivel(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoMinimoPorNivel form,
            BindingResult erros,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                boolean mudou =
                        regras.alterarMinimoPorNivel(ministerioId, form.ligada(), form.nivelId(), form.minimo(), autor);
                redirecionamento.addFlashAttribute(
                        "sucesso",
                        !mudou
                                ? "O mínimo por nível não mudou"
                                : (form.ligada() ? "Mínimo por nível ligado" : "Mínimo por nível desligado"));
                return paraALista(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formularioDoMinimo(ministerioId, form, model);
    }

    private String formularioDoLimite(Long ministerioId, DadosDoLimite form, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("form", form);
        return LIMITE;
    }

    private String formularioDoMinimo(Long ministerioId, DadosDoMinimoPorNivel form, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("form", form);
        model.addAttribute(
                "niveis",
                niveis.listar(ministerioId).stream()
                        .map(nivel -> new Opcao(nivel.getId().toString(), nivel.getNome()))
                        .toList());
        return MINIMO_POR_NIVEL;
    }

    private static String paraALista(Long ministerioId) {
        return "redirect:/ministerios/" + ministerioId + "/regras";
    }
}
