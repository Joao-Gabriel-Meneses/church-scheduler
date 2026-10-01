package br.igreja.escala.ministerio.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.ministerio.service.DadosDaFuncao;
import br.igreja.escala.ministerio.service.FuncaoService;
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

/** Funções e níveis do ministério (a página) e o cadastro de funções. Os níveis ficam no NivelController. */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/funcoes")
class FuncaoController {

    static final String PAGINA = "ministerio/funcoes";
    private static final String FORMULARIO = "ministerio/funcao-form";

    private final MinisterioService ministerios;
    private final FuncaoService funcoes;
    private final NivelService niveis;

    FuncaoController(MinisterioService ministerios, FuncaoService funcoes, NivelService niveis) {
        this.ministerios = ministerios;
        this.funcoes = funcoes;
        this.niveis = niveis;
    }

    @GetMapping
    String pagina(@PathVariable Long ministerioId, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("funcoes", funcoes.resumos(ministerioId));
        model.addAttribute("niveis", niveis.resumos(ministerioId));
        return PAGINA;
    }

    @GetMapping("/nova")
    String nova(@PathVariable Long ministerioId, Model model) {
        return formulario(ministerioId, null, DadosDaFuncao.nova(), "Criar função", model);
    }

    @PostMapping
    String criar(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDaFuncao form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var criada = funcoes.criar(ministerioId, form);
                redirecionamento.addFlashAttribute("sucesso", "Função " + criada.getNome() + " criada");
                return paraAPagina(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, null, form, "Criar função", model);
    }

    @GetMapping("/{funcaoId}")
    String editar(@PathVariable Long ministerioId, @PathVariable Long funcaoId, Model model) {
        var funcao = funcoes.buscar(ministerioId, funcaoId);
        return formulario(ministerioId, funcaoId, DadosDaFuncao.de(funcao), funcao.getNome(), model);
    }

    @PostMapping("/{funcaoId}")
    String alterar(
            @PathVariable Long ministerioId,
            @PathVariable Long funcaoId,
            @Valid @ModelAttribute("form") DadosDaFuncao form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        var atual = funcoes.buscar(ministerioId, funcaoId);
        if (!erros.hasErrors()) {
            try {
                var alterada = funcoes.alterar(ministerioId, funcaoId, form);
                redirecionamento.addFlashAttribute("sucesso", "Função " + alterada.getNome() + " salva");
                return paraAPagina(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, funcaoId, form, atual.getNome(), model);
    }

    @PostMapping("/{funcaoId}/excluir")
    String excluir(@PathVariable Long ministerioId, @PathVariable Long funcaoId, RedirectAttributes redirecionamento) {
        try {
            var excluida = funcoes.excluir(ministerioId, funcaoId);
            redirecionamento.addFlashAttribute("sucesso", "Função " + excluida.getNome() + " excluída");
            return paraAPagina(ministerioId);
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
            return "redirect:/ministerios/" + ministerioId + "/funcoes/" + funcaoId;
        }
    }

    private String formulario(Long ministerioId, Long funcaoId, DadosDaFuncao form, String cabecalho, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("funcaoId", funcaoId);
        model.addAttribute("form", form);
        model.addAttribute("cabecalho", cabecalho);
        model.addAttribute("icones", Opcoes.icones());
        return FORMULARIO;
    }

    static String paraAPagina(Long ministerioId) {
        return "redirect:/ministerios/" + ministerioId + "/funcoes";
    }
}
