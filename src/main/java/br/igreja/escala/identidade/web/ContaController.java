package br.igreja.escala.identidade.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.DadosDaConta;
import br.igreja.escala.identidade.service.SenhaRecusadaException;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.identidade.web.dto.TrocaDeSenhaForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A própria conta: nome, e-mail e telefone em /conta e a troca de senha em /conta/senha. Com senha provisória, só a troca
 * de senha abre (SenhaProvisoriaInterceptor). Trocar a senha encerra as outras sessões da pessoa (outro celular, outro
 * navegador); a desta requisição continua.
 */
@Controller
@RequestMapping("/conta")
class ContaController {

    private static final String DADOS = "identidade/conta";
    private static final String SENHA = "identidade/senha";

    private final UsuarioService usuarios;
    private final SessoesAbertas sessoesAbertas;
    private final SecurityContextRepository sessoes = new HttpSessionSecurityContextRepository();

    ContaController(UsuarioService usuarios, SessoesAbertas sessoesAbertas) {
        this.usuarios = usuarios;
        this.sessoesAbertas = sessoesAbertas;
    }

    @GetMapping
    String dados(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("form", DadosDaConta.de(usuarios.buscar(usuario.getId())));
        return DADOS;
    }

    @PostMapping
    String salvarDados(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @ModelAttribute("form") DadosDaConta form,
            BindingResult erros,
            HttpServletRequest requisicao,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        if (erros.hasErrors()) {
            return DADOS;
        }
        try {
            atualizarSessao(usuarios.editarPropriaConta(usuario.getId(), form), requisicao, resposta);
        } catch (RegraVioladaException recusa) {
            Formularios.rejeitar(erros, recusa);
            return DADOS;
        }
        redirecionamento.addFlashAttribute("sucesso", "Dados salvos");
        return "redirect:/";
    }

    @GetMapping("/senha")
    String formulario(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("form", TrocaDeSenhaForm.vazio());
        model.addAttribute("provisoria", usuario.isSenhaProvisoria());
        return SENHA;
    }

    @PostMapping("/senha")
    String trocar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @ModelAttribute("form") TrocaDeSenhaForm form,
            BindingResult erros,
            Model model,
            HttpServletRequest requisicao,
            HttpServletResponse resposta,
            RedirectAttributes redirecionamento) {
        model.addAttribute("provisoria", usuario.isSenhaProvisoria());
        if (!erros.hasFieldErrors("novaSenha") && !form.confirmacaoConfere()) {
            erros.rejectValue("confirmacao", "Diferente", "Repita a nova senha igual nos dois campos.");
        }
        if (erros.hasErrors()) {
            return SENHA;
        }
        try {
            var atualizado = usuarios.trocarSenha(usuario.getId(), form.senhaAtual(), form.novaSenha());
            atualizarSessao(atualizado, requisicao, resposta);
            sessoesAbertas.encerrarOutras(
                    usuario.getId(), requisicao.getSession().getId());
        } catch (SenhaRecusadaException recusa) {
            erros.rejectValue(recusa.campo(), "Recusada", recusa.getMessage());
            return SENHA;
        }
        redirecionamento.addFlashAttribute("sucesso", "Senha salva");
        return "redirect:/";
    }

    /**
     * A sessão guarda o usuário do login; sem trocá-lo, a marca de senha provisória (ou o nome e o e-mail antigos)
     * continuaria nela.
     */
    private void atualizarSessao(
            UsuarioAutenticado atualizado, HttpServletRequest requisicao, HttpServletResponse resposta) {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(atualizado, null, atualizado.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        sessoes.saveContext(contexto, requisicao, resposta);
    }
}
