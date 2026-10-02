package br.igreja.escala.evento.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.web.Formularios;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.DadosDoEvento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Eventos do mês do ministério: a lista com o seletor de período, gerar dos modelos e os avulsos. */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/eventos")
class EventoController {

    static final String LISTA = "evento/eventos";
    private static final String FORMULARIO = "evento/evento-form";

    private final MinisterioService ministerios;
    private final EventoService eventos;
    private final ModeloEventoService modelos;
    private final PeriodoService periodos;
    private final FuncaoService funcoes;

    EventoController(
            MinisterioService ministerios,
            EventoService eventos,
            ModeloEventoService modelos,
            PeriodoService periodos,
            FuncaoService funcoes) {
        this.ministerios = ministerios;
        this.eventos = eventos;
        this.modelos = modelos;
        this.periodos = periodos;
        this.funcoes = funcoes;
    }

    /** Sem {@code mes}, abre o mês seguinte, que é o que o gerente prepara. */
    @GetMapping
    String lista(
            @PathVariable Long ministerioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("mes", doMes);
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(doMes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(doMes));
        model.addAttribute("mesCurto", Datas.mesCurto(doMes));
        model.addAttribute("mesAnterior", doMes.minusMonths(1));
        model.addAttribute("proximoMes", doMes.plusMonths(1));
        model.addAttribute(
                "disponibilidadeTravada",
                periodos.doMes(ministerioId, doMes)
                        .map(Periodo::isDisponibilidadeTravada)
                        .orElse(false));
        model.addAttribute("eventos", eventos.doMes(ministerioId, doMes));
        model.addAttribute("temModelosAtivos", !modelos.ativos(ministerioId).isEmpty());
        return LISTA;
    }

    @PostMapping("/gerar")
    String gerar(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            RedirectAttributes redirecionamento) {
        String nomeDoMes = Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
        try {
            int criados = eventos.gerarDoMes(ministerioId, mes);
            redirecionamento.addFlashAttribute(
                    "sucesso",
                    switch (criados) {
                        case 0 -> "Nenhum evento novo em " + nomeDoMes + ": os dos modelos já existem";
                        case 1 -> "1 evento criado em " + nomeDoMes;
                        default -> criados + " eventos criados em " + nomeDoMes;
                    });
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return paraOMes(ministerioId, mes);
    }

    @GetMapping("/novo")
    String novo(@PathVariable Long ministerioId, Model model) {
        return formulario(ministerioId, null, DadosDoEvento.vazio(idsDasFuncoes(ministerioId)), model);
    }

    @PostMapping
    String criar(
            @PathVariable Long ministerioId,
            @Valid @ModelAttribute("form") DadosDoEvento form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        if (!erros.hasErrors()) {
            try {
                var criado = eventos.criarAvulso(ministerioId, form);
                redirecionamento.addFlashAttribute("sucesso", "Evento " + criado.getNome() + " criado");
                return paraOMes(ministerioId, YearMonth.from(criado.getData()));
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, null, form, model);
    }

    @GetMapping("/{eventoId}")
    String editar(@PathVariable Long ministerioId, @PathVariable Long eventoId, Model model) {
        var evento = eventos.buscar(ministerioId, eventoId);
        return formulario(ministerioId, evento, DadosDoEvento.de(evento, idsDasFuncoes(ministerioId)), model);
    }

    @PostMapping("/{eventoId}")
    String alterar(
            @PathVariable Long ministerioId,
            @PathVariable Long eventoId,
            @Valid @ModelAttribute("form") DadosDoEvento form,
            BindingResult erros,
            Model model,
            RedirectAttributes redirecionamento) {
        var evento = eventos.buscar(ministerioId, eventoId);
        if (!erros.hasErrors()) {
            try {
                var alterado = eventos.alterar(ministerioId, eventoId, form);
                redirecionamento.addFlashAttribute("sucesso", "Evento " + descrever(alterado) + " salvo");
                return paraOMes(ministerioId, YearMonth.from(alterado.getData()));
            } catch (RegraVioladaException recusa) {
                Formularios.rejeitar(erros, recusa);
            }
        }
        return formulario(ministerioId, evento, form, model);
    }

    @PostMapping("/{eventoId}/cancelar")
    String cancelar(@PathVariable Long ministerioId, @PathVariable Long eventoId, RedirectAttributes redirecionamento) {
        var evento = eventos.cancelar(ministerioId, eventoId);
        redirecionamento.addFlashAttribute("sucesso", descrever(evento) + " cancelado");
        return paraOMes(ministerioId, YearMonth.from(evento.getData()));
    }

    @PostMapping("/{eventoId}/reativar")
    String reativar(@PathVariable Long ministerioId, @PathVariable Long eventoId, RedirectAttributes redirecionamento) {
        var evento = eventos.reativar(ministerioId, eventoId);
        redirecionamento.addFlashAttribute("sucesso", descrever(evento) + " reativado");
        return paraOMes(ministerioId, YearMonth.from(evento.getData()));
    }

    /** Novo (evento nulo) ou edição. Num evento do modelo, a data não é campo: vai escondida e aparece como texto. */
    private String formulario(Long ministerioId, Evento evento, DadosDoEvento form, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("evento", evento);
        model.addAttribute("form", form);
        model.addAttribute("caixasDeFuncao", CaixaDeFuncao.de(funcoes.listar(ministerioId), form.funcoes()));
        if (evento != null) {
            model.addAttribute("descricao", descrever(evento));
            model.addAttribute("dataDoEvento", Datas.dataCurta(evento.getData()));
            model.addAttribute(
                    "doModelo", evento.isAvulso() ? null : evento.getModelo().getNome());
        }
        return FORMULARIO;
    }

    private List<Long> idsDasFuncoes(Long ministerioId) {
        return funcoes.listar(ministerioId).stream().map(Funcao::getId).toList();
    }

    /** "Culto de domingo de 04/10". */
    private static String descrever(Evento evento) {
        return evento.getNome() + " de " + Datas.diaEMes(evento.getData());
    }

    private static String paraOMes(Long ministerioId, YearMonth mes) {
        return "redirect:/ministerios/" + ministerioId + "/eventos?mes=" + mes;
    }
}
