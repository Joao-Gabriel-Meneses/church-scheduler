package br.igreja.escala.ministerio.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.ministerio.service.DadosDoMinisterio;
import br.igreja.escala.ministerio.service.MinisterioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Cadastro de ministérios, só do admin. Os gerentes são nomeados na página de cada membro. */
@Controller
@RequestMapping("/admin/ministerios")
@PreAuthorize("hasRole('ADMIN')")
class AdminMinisterioController {

    private static final String LISTA = "ministerio/ministerios";
    private static final String FORMULARIO = "ministerio/ministerio-form";
    private static final String VOLTAR_PARA_A_LISTA = "redirect:/admin/ministerios";

    private final MinisterioService ministerios;

    AdminMinisterioController(MinisterioService ministerios) {
        this.ministerios = ministerios;
    }

    /** "cadastrados", e não "ministerios": esse nome é da SideRail do layout (NavegacaoAdvice). */
    @GetMapping
    String lista(Model model) {
        model.addAttribute("cadastrados", ministerios.resumos());
        return LISTA;
    }

    @GetMapping("/novo")
    String novo(Model model) {
        return formulario(model, DadosDoMinisterio.vazio(), null, "Criar ministério");
    }

    @PostMapping
    String criar(
            @Valid @ModelAttribute("form") DadosDoMinisterio form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var criado = ministerios.criar(form);
                redirecionamento.addFlashAttribute("sucesso", "Ministério " + criado.getNome() + " criado");
                return VOLTAR_PARA_A_LISTA;
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(model, form, null, "Criar ministério");
    }

    @GetMapping("/{id}")
    String editar(@PathVariable Long id, Model model) {
        var ministerio = ministerios.buscar(id);
        return formulario(model, DadosDoMinisterio.de(ministerio), id, ministerio.getNome());
    }

    @PostMapping("/{id}")
    String alterar(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") DadosDoMinisterio form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        var atual = ministerios.buscar(id);
        if (!erros.hasErrors()) {
            try {
                var alterado = ministerios.alterar(id, form);
                redirecionamento.addFlashAttribute("sucesso", "Ministério " + alterado.getNome() + " salvo");
                return VOLTAR_PARA_A_LISTA;
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(model, form, id, atual.getNome());
    }

    private static String formulario(Model model, DadosDoMinisterio form, Long id, String cabecalho) {
        model.addAttribute("form", form);
        model.addAttribute("ministerioId", id);
        model.addAttribute("cabecalho", cabecalho);
        model.addAttribute("cores", Opcoes.cores());
        model.addAttribute("icones", Opcoes.icones());
        return FORMULARIO;
    }
}
