package br.igreja.escala.escala.web;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.service.Andamento;
import br.igreja.escala.escala.service.ConsultaDaEscala;
import br.igreja.escala.escala.service.GeracaoDaEscala;
import br.igreja.escala.escala.service.ImpressaoDaEscala;
import br.igreja.escala.escala.service.PaginaDaEscala;
import br.igreja.escala.escala.service.PublicacaoDaEscala;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.web.GerenteDoMinisterio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A escala do mês para o gerente: a grade com as vagas vazias explicadas e o resumo, gerar (o primário sem rascunho),
 * publicar (o primário com rascunho, depois do resumo) e reabrir para rascunho. A geração roda em segundo plano; a
 * página mostra o andamento por htmx e recarrega quando termina. O ajuste de cada vaga fica no VagaController. Com a
 * escala publicada, o PDF do mês para imprimir.
 */
@Controller
@GerenteDoMinisterio
@RequestMapping("/ministerios/{ministerioId}/escalas")
class EscalaController {

    static final String PAGINA = "escala/escalas";
    static final String ANDAMENTO = "escala/fragments/andamento :: andamento";

    private final ConsultaDaEscala consulta;
    private final GeracaoDaEscala geracao;
    private final PublicacaoDaEscala publicacao;
    private final ImpressaoDaEscala impressao;
    private final MinisterioService ministerios;
    private final EventoService eventos;
    private final Clock relogio;
    private final Duration limite;

    EscalaController(
            ConsultaDaEscala consulta,
            GeracaoDaEscala geracao,
            PublicacaoDaEscala publicacao,
            ImpressaoDaEscala impressao,
            MinisterioService ministerios,
            EventoService eventos,
            Clock relogio,
            @Value("${timefold.solver.termination.spent-limit:30s}") Duration limite) {
        this.consulta = consulta;
        this.geracao = geracao;
        this.publicacao = publicacao;
        this.impressao = impressao;
        this.ministerios = ministerios;
        this.eventos = eventos;
        this.relogio = relogio;
        this.limite = limite;
    }

    /**
     * Sem {@code mes}, abre o mês seguinte, que é o que o gerente prepara. Uma geração que terminou e ainda não foi
     * avisada vira o toast (ou o alerta, se falhou).
     */
    @GetMapping
    String pagina(
            @PathVariable Long ministerioId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            Model model) {
        var doMes = mes == null ? eventos.proximoMes() : mes;
        preencherMes(ministerioId, doMes, model);
        geracao.retirarTerminada(ministerioId, doMes)
                .ifPresent(terminada -> model.addAttribute(
                        terminada.getEstado() == Andamento.Estado.CONCLUIDA ? "sucesso" : "recusa",
                        terminada.getMensagem()));
        var geracaoNaTela = emAndamento(ministerioId, doMes);
        var pagina = consulta.doMes(ministerioId, doMes);
        model.addAttribute("geracao", geracaoNaTela);
        model.addAttribute("pagina", pagina);
        if (geracaoNaTela == null) {
            adicionarResumoDaPublicacao(ministerioId, pagina, model);
        }
        return PAGINA;
    }

    /** Publica a escala do mês, mesmo com vaga vazia: o gerente já viu o resumo. */
    @PostMapping("/publicar")
    String publicar(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        ministerios.buscar(ministerioId);
        try {
            redirecionamento.addFlashAttribute("sucesso", publicacao.publicar(ministerioId, mes, autor));
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return "redirect:" + caminho(ministerioId, mes);
    }

    /** A escala volta a ser rascunho e sai da visão dos membros. */
    @PostMapping("/reabrir")
    String reabrir(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        ministerios.buscar(ministerioId);
        try {
            redirecionamento.addFlashAttribute("sucesso", publicacao.reabrir(ministerioId, mes, autor));
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return "redirect:" + caminho(ministerioId, mes);
    }

    /** O PDF da escala publicada do mês (A4), para baixar. Rascunho responde 404. */
    @GetMapping("/pdf")
    ResponseEntity<byte[]> pdf(
            @PathVariable Long ministerioId, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        var arquivo = impressao.doMes(ministerioId, mes);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(arquivo.nome(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(arquivo.conteudo());
    }

    /** O resumo do Sheet de publicar, só quando há rascunho para publicar. */
    static void adicionarResumoDaPublicacao(
            Long ministerioId, PaginaDaEscala pagina, PublicacaoDaEscala publicacao, Model model) {
        if (pagina.gerada() && !pagina.publicada()) {
            model.addAttribute("resumoDaPublicacao", publicacao.resumo(ministerioId, pagina.mes()));
        }
    }

    private void adicionarResumoDaPublicacao(Long ministerioId, PaginaDaEscala pagina, Model model) {
        adicionarResumoDaPublicacao(ministerioId, pagina, publicacao, model);
    }

    /** Começa a geração; o segundo clique (ou um POST repetido) encontra a que está rodando. */
    @PostMapping("/gerar")
    String gerar(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @AuthenticationPrincipal UsuarioAutenticado autor,
            RedirectAttributes redirecionamento) {
        ministerios.buscar(ministerioId);
        try {
            var jaGerava = geracao.andamento(ministerioId, mes).filter(Andamento::isGerando);
            var andamento = geracao.iniciar(ministerioId, mes, autor);
            if (jaGerava.isPresent() && jaGerava.get() == andamento) {
                redirecionamento.addFlashAttribute(
                        "sucesso", "A escala de " + nomeDoMes(mes) + " já está sendo gerada");
            }
        } catch (RegraVioladaException recusa) {
            redirecionamento.addFlashAttribute("recusa", recusa.getMessage());
        }
        return "redirect:" + caminho(ministerioId, mes);
    }

    /**
     * O painel de andamento, que o htmx pede a cada 2 s. Quando a geração termina, manda o htmx recarregar a página
     * inteira (HX-Redirect), que mostra o rascunho e avisa o resultado. Sem htmx, volta para a página.
     */
    @GetMapping("/andamento")
    String andamento(
            @PathVariable Long ministerioId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
            @RequestHeader(name = "HX-Request", required = false) String htmx,
            HttpServletRequest requisicao,
            HttpServletResponse resposta,
            Model model) {
        ministerios.buscar(ministerioId);
        var andamento = emAndamento(ministerioId, mes);
        if (!"true".equals(htmx)) {
            return "redirect:" + caminho(ministerioId, mes);
        }
        if (andamento == null) {
            resposta.setHeader("HX-Redirect", requisicao.getContextPath() + caminho(ministerioId, mes));
            return null;
        }
        preencherMes(ministerioId, mes, model);
        model.addAttribute("geracao", andamento);
        return ANDAMENTO;
    }

    private GeracaoNaTela emAndamento(Long ministerioId, YearMonth mes) {
        return geracao.andamento(ministerioId, mes)
                .filter(Andamento::isGerando)
                .map(andamento -> new GeracaoNaTela(
                        Duration.between(andamento.getInicio(), Instant.now(relogio))
                                .toSeconds(),
                        limite.toSeconds(),
                        andamento.getPreenchidas(),
                        andamento.getVagas()))
                .orElse(null);
    }

    private void preencherMes(Long ministerioId, YearMonth mes, Model model) {
        model.addAttribute("ministerio", ministerios.buscar(ministerioId));
        model.addAttribute("mes", mes);
        model.addAttribute("nomeDoMes", Datas.nomeDoMes(mes));
        model.addAttribute("mesPorExtenso", Datas.mesPorExtenso(mes));
        model.addAttribute("mesCurto", Datas.mesCurto(mes));
        model.addAttribute("mesAnterior", mes.minusMonths(1));
        model.addAttribute("proximoMes", mes.plusMonths(1));
    }

    private static String nomeDoMes(YearMonth mes) {
        return Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
    }

    private static String caminho(Long ministerioId, YearMonth mes) {
        return "/ministerios/" + ministerioId + "/escalas?mes=" + mes;
    }
}
