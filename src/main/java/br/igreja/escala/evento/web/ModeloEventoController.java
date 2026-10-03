package br.igreja.escala.evento.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.compartilhado.web.Opcao;
import br.igreja.escala.evento.service.DadosDoModelo;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Modelos de evento do ministério (Culto de domingo, 18h00), de onde saem os eventos de cada mês. */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/eventos/modelos")
class ModeloEventoController {

    static final String LISTA = "evento/modelos";
    private static final String FORMULARIO = "evento/modelo-form";

    private final MinisterioService ministerios;
    private final ModeloEventoService modelos;
    private final FuncaoService funcoes;

    ModeloEventoController(MinisterioService ministerios, ModeloEventoService modelos, FuncaoService funcoes) {
        this.ministerios = ministerios;
        this.modelos = modelos;
        this.funcoes = funcoes;
    }

    @GetMapping
    String lista(@PathVariable Long ministerioId, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("modelos", modelos.resumos(ministerioId));
        return LISTA;
    }

    @GetMapping("/novo")
    String novo(@PathVariable Long ministerioId, Model model) {
        return formulario(ministerioId, null, DadosDoModelo.novo(idsDasFuncoes(ministerioId)), "Criar modelo", model);
    }

    @PostMapping
    String criar(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoModelo form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var criado = modelos.criar(ministerioId, form);
                redirecionamento.addFlashAttribute("sucesso", "Modelo " + criado.getNome() + " criado");
                return paraALista(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, null, form, "Criar modelo", model);
    }

    @GetMapping("/{modeloId}")
    String editar(@PathVariable Long ministerioId, @PathVariable Long modeloId, Model model) {
        var modelo = modelos.buscar(ministerioId, modeloId);
        return formulario(
                ministerioId, modeloId, DadosDoModelo.de(modelo, idsDasFuncoes(ministerioId)), modelo.getNome(), model);
    }

    @PostMapping("/{modeloId}")
    String alterar(
            @PathVariable Long ministerioId,
            @PathVariable Long modeloId,
            @Valid @ModelAttribute("form") DadosDoModelo form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        var atual = modelos.buscar(ministerioId, modeloId);
        if (!erros.hasErrors()) {
            try {
                var alterado = modelos.alterar(ministerioId, modeloId, form);
                redirecionamento.addFlashAttribute("sucesso", "Modelo " + alterado.getNome() + " salvo");
                return paraALista(ministerioId);
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, modeloId, form, atual.getNome(), model);
    }

    private String formulario(Long ministerioId, Long modeloId, DadosDoModelo form, String cabecalho, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("modeloId", modeloId);
        model.addAttribute("form", form);
        model.addAttribute("cabecalho", cabecalho);
        model.addAttribute("dias", diasDaSemana());
        model.addAttribute("caixasDeFuncao", CaixaDeFuncao.de(funcoes.listar(ministerioId), form.funcoes()));
        return FORMULARIO;
    }

    private List<Long> idsDasFuncoes(Long ministerioId) {
        return funcoes.listar(ministerioId).stream().map(Funcao::getId).toList();
    }

    /** Do domingo ao sábado, como a igreja conta a semana. */
    static List<Opcao> diasDaSemana() {
        return Stream.concat(
                        Stream.of(DayOfWeek.SUNDAY),
                        Stream.of(DayOfWeek.values()).limit(6))
                .map(dia -> new Opcao(dia.name(), Datas.diaDaSemana(dia)))
                .toList();
    }

    private static String paraALista(Long ministerioId) {
        return "redirect:/ministerios/" + ministerioId + "/eventos/modelos";
    }
}
