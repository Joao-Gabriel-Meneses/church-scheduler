package br.igreja.escala.ministerio.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.ministerio.service.DadosDoNivel;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Cadastro de níveis. Fica sob /funcoes porque a lista é a mesma página das funções. */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/funcoes/niveis")
class NivelController {

    private static final String FORMULARIO = "ministerio/nivel-form";

    private final MinisterioService ministerios;
    private final NivelService niveis;

    NivelController(MinisterioService ministerios, NivelService niveis) {
        this.ministerios = ministerios;
        this.niveis = niveis;
    }

    @GetMapping("/novo")
    String novo(@PathVariable Long ministerioId, Model model) {
        return formulario(
                ministerioId, null, DadosDoNivel.novo(niveis.proximaOrdem(ministerioId)), "Criar nível", model);
    }

    @PostMapping
    String criar(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoNivel form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var criado = niveis.criar(ministerioId, form);
                redirecionamento.addFlashAttribute("sucesso", "Nível " + criado.getNome() + " criado");
                return FuncaoController.paraAPagina(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, null, form, "Criar nível", model);
    }

    @GetMapping("/{nivelId}")
    String editar(@PathVariable Long ministerioId, @PathVariable Long nivelId, Model model) {
        var nivel = niveis.buscar(ministerioId, nivelId);
        return formulario(ministerioId, nivelId, DadosDoNivel.de(nivel), nivel.getNome(), model);
    }

    @PostMapping("/{nivelId}")
    String alterar(
            @PathVariable Long ministerioId,
            @PathVariable Long nivelId,
            @Valid @ModelAttribute("form") DadosDoNivel form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        var atual = niveis.buscar(ministerioId, nivelId);
        if (!erros.hasErrors()) {
            try {
                var alterado = niveis.alterar(ministerioId, nivelId, form);
                redirecionamento.addFlashAttribute("sucesso", "Nível " + alterado.getNome() + " salvo");
                return FuncaoController.paraAPagina(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, nivelId, form, atual.getNome(), model);
    }

    @PostMapping("/{nivelId}/excluir")
    String excluir(@PathVariable Long ministerioId, @PathVariable Long nivelId, RedirectAttributes redirecionamento) {
        try {
            var excluido = niveis.excluir(ministerioId, nivelId);
            redirecionamento.addFlashAttribute("sucesso", "Nível " + excluido.getNome() + " excluído");
            return FuncaoController.paraAPagina(ministerioId);
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
            return "redirect:/ministerios/" + ministerioId + "/funcoes/niveis/" + nivelId;
        }
    }

    private String formulario(Long ministerioId, Long nivelId, DadosDoNivel form, String cabecalho, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("nivelId", nivelId);
        model.addAttribute("form", form);
        model.addAttribute("cabecalho", cabecalho);
        return FORMULARIO;
    }
}
