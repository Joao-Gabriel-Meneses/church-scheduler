package br.igreja.escala.ministerio.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.DadosDoMembro;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Membros do ministério: lista, cadastro com senha provisória e a página de cada um. Nomear e remover gerente é do
 * admin: o {@code @PreAuthorize} do método vale no lugar do {@link GerenteDoMinisterio} da classe.
 */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/membros")
class MembroController {

    static final String LISTA = "ministerio/membros";
    static final String PAGINA_DO_MEMBRO = "ministerio/membro";
    private static final String FORMULARIO = "ministerio/membro-form";

    private final MinisterioService ministerios;
    private final MembroService membros;

    MembroController(MinisterioService ministerios, MembroService membros) {
        this.ministerios = ministerios;
        this.membros = membros;
    }

    @GetMapping
    String lista(@PathVariable Long ministerioId, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("membros", membros.listar(ministerioId));
        return LISTA;
    }

    @GetMapping("/novo")
    String novo(@PathVariable Long ministerioId, Model model) {
        model.addAttribute("form", DadosDoMembro.vazio());
        return formulario(ministerioId, model);
    }

    @PostMapping
    String cadastrar(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoMembro form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var resultado = membros.cadastrar(ministerioId, form);
                String nome = resultado.membro().nome();
                redirecionamento.addFlashAttribute(
                        "sucesso",
                        resultado.jaTinhaConta()
                                ? nome + " já tinha conta e entrou no ministério"
                                : nome + " entrou no ministério com a senha provisória");
                return paraOMembro(ministerioId, resultado.membro().id());
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, model);
    }

    @GetMapping("/{usuarioId}")
    String membro(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            Model model) {
        var membro = membros.buscar(ministerioId, usuarioId);
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("membro", membro);
        model.addAttribute("podeMexerNaConta", membros.podeMexerNaConta(membro, autor));
        model.addAttribute("podeRemover", autor.isAdmin() || !membro.gerente());
        model.addAttribute("ehAdmin", autor.isAdmin());
        model.addAttribute("ehVoce", autor.getId().equals(usuarioId));
        return PAGINA_DO_MEMBRO;
    }

    @PostMapping("/{usuarioId}/senha")
    String redefinirSenha(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "") String senha,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        try {
            var pessoa = membros.redefinirSenha(ministerioId, usuarioId, senha, autor);
            redirecionamento.addFlashAttribute("sucesso", "Senha provisória de " + pessoa.nome() + " redefinida");
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return paraOMembro(ministerioId, usuarioId);
    }

    @PostMapping("/{usuarioId}/remover")
    String remover(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        try {
            var pessoa = membros.remover(ministerioId, usuarioId, autor);
            redirecionamento.addFlashAttribute("sucesso", pessoa.nome() + " saiu do ministério");
            return "redirect:/ministerios/" + ministerioId + "/membros";
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
            return paraOMembro(ministerioId, usuarioId);
        }
    }

    @PostMapping("/{usuarioId}/gerente")
    @PreAuthorize("hasRole('ADMIN')")
    String tornarGerente(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        var pessoa = membros.tornarGerente(ministerioId, usuarioId, autor);
        redirecionamento.addFlashAttribute("sucesso", pessoa.nome() + " agora é gerente");
        return paraOMembro(ministerioId, usuarioId);
    }

    @PostMapping("/{usuarioId}/gerente/remover")
    @PreAuthorize("hasRole('ADMIN')")
    String removerGerente(
            @PathVariable Long ministerioId,
            @PathVariable Long usuarioId,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        var pessoa = membros.removerGerente(ministerioId, usuarioId, autor);
        redirecionamento.addFlashAttribute("sucesso", pessoa.nome() + " deixou de ser gerente");
        return paraOMembro(ministerioId, usuarioId);
    }

    private String formulario(Long ministerioId, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        return FORMULARIO;
    }

    static String paraOMembro(Long ministerioId, Long usuarioId) {
        return "redirect:/ministerios/" + ministerioId + "/membros/" + usuarioId;
    }
}
