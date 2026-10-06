package br.igreja.escala.escala.service;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.service.ResumoDaPublicacao.Item;
import br.igreja.escala.escala.solver.DiagnosticoDaVaga;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.escala.solver.ValidacaoDaVaga;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publicar a escala do mês (o rascunho vira o que os membros veem) e reabrir para rascunho (sai da visão dos membros e a
 * geração automática volta a valer). Publica mesmo com vaga vazia, depois de o gerente ver o resumo. Os dois ficam na
 * auditoria. E-mail de publicação é da Fase 4.
 */
@Service
public class PublicacaoDaEscala {

    private final LeituraDoPeriodo leitura;
    private final PeriodoService periodos;
    private final GeracoesEmAndamento andamentos;
    private final SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager;
    private final AuditoriaService auditoria;
    private final MinisterioService ministerios;

    @Autowired
    PublicacaoDaEscala(
            LeituraDoPeriodo leitura,
            PeriodoService periodos,
            GeracoesEmAndamento andamentos,
            SolverManager<EscalaDoPeriodo> solverManager,
            AuditoriaService auditoria,
            MinisterioService ministerios) {
        this(leitura, periodos, andamentos, SolutionManager.create(solverManager), auditoria, ministerios);
    }

    PublicacaoDaEscala(
            LeituraDoPeriodo leitura,
            PeriodoService periodos,
            GeracoesEmAndamento andamentos,
            SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager,
            AuditoriaService auditoria,
            MinisterioService ministerios) {
        this.leitura = leitura;
        this.periodos = periodos;
        this.andamentos = andamentos;
        this.solutionManager = solutionManager;
        this.auditoria = auditoria;
        this.ministerios = ministerios;
    }

    /** O resumo antes de publicar, com os dados de agora. */
    @Transactional(readOnly = true)
    public ResumoDaPublicacao resumo(Long ministerioId, YearMonth mes) {
        var dados = leitura.ler(ministerioId, mes);
        if (dados.periodo() == null) {
            return new ResumoDaPublicacao(List.of(), List.of(), List.of());
        }
        var escala = MontagemDaEscala.montar(dados, false);
        var diagnostico = new DiagnosticoDaVaga(escala, dados.regras(), dados.nomesDosNiveis());
        var validacao = new ValidacaoDaVaga(solutionManager, escala, dados.regras(), dados.nomesDosNiveis());
        var vazias = escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() == null && vaga.isObrigatoria())
                .map(vaga -> new Item(onde(vaga), diagnostico.motivo(vaga).texto()))
                .toList();
        Map<Long, String> justificativas = dados.vagas().stream()
                .filter(vaga -> vaga.isForcada())
                .collect(Collectors.toMap(vaga -> vaga.getId(), vaga -> vaga.getJustificativa()));
        var avisos = validacao.avisos();
        var forcadas = escala.getVagas().stream()
                .filter(vaga -> justificativas.containsKey(vaga.getId()))
                .map(vaga -> new Item(
                        vaga.getPessoa().nome() + " em " + onde(vaga),
                        regras(avisos.getOrDefault(vaga.getId(), List.of())) + justificativas.get(vaga.getId())))
                .toList();
        var regras = new ArrayList<Item>();
        validacao.penalidadesBrandas().forEach((tipo, penalidade) -> regras.add(regra(tipo, penalidade, escala)));
        return new ResumoDaPublicacao(vazias, forcadas, regras);
    }

    /**
     * @return o texto do aviso de sucesso
     * @throws RegraVioladaException se a escala ainda não foi gerada ou está sendo gerada
     */
    @Transactional
    public String publicar(Long ministerioId, YearMonth mes, UsuarioAutenticado autor) {
        var periodo = exigirPeriodo(ministerioId, mes, "publicar");
        String nomeDoMes = nomeDoMes(mes);
        if (gerando(periodo)) {
            throw RegraVioladaException.geral(
                    "A escala de " + nomeDoMes + " está sendo gerada. Espere terminar para publicar.");
        }
        var dados = leitura.ler(ministerioId, mes);
        var escala = MontagemDaEscala.montar(dados, false);
        if (dados.vagas().isEmpty()) {
            throw RegraVioladaException.geral("Gere a escala de " + nomeDoMes + " antes de publicar.");
        }
        if (!periodos.publicarEscala(periodo.getId())) {
            return "A escala de " + nomeDoMes + " já estava publicada";
        }
        long total = escala.getVagas().size();
        long preenchidas = escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() != null)
                .count();
        long vazias = escala.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() == null && vaga.isObrigatoria())
                .count();
        long forcadas = dados.vagas().stream().filter(vaga -> vaga.isForcada()).count();
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.PUBLICAR_ESCALA,
                autor.getId(),
                ministerioId,
                null,
                "Escala de " + Datas.mesPorExtenso(mes) + " publicada (" + dados.nomeDoMinisterio() + "): "
                        + preenchidas + " de " + total + " vagas por vir preenchidas, " + vazias
                        + (vazias == 1 ? " obrigatória vazia, " : " obrigatórias vazias, ") + forcadas
                        + (forcadas == 1 ? " forçada." : " forçadas.")));
        return "Escala de " + nomeDoMes + " publicada";
    }

    /**
     * @return o texto do aviso de sucesso
     * @throws RegraVioladaException se o mês não tem eventos
     */
    @Transactional
    public String reabrir(Long ministerioId, YearMonth mes, UsuarioAutenticado autor) {
        var periodo = exigirPeriodo(ministerioId, mes, "reabrir");
        String nomeDoMes = nomeDoMes(mes);
        if (!periodos.reabrirEscala(periodo.getId())) {
            return "A escala de " + nomeDoMes + " já era rascunho";
        }
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.REABRIR_ESCALA,
                autor.getId(),
                ministerioId,
                null,
                "Escala de " + Datas.mesPorExtenso(mes) + " reaberta para rascunho ("
                        + leitura.ler(ministerioId, mes).nomeDoMinisterio() + "): saiu da visão dos membros."));
        return "Escala de " + nomeDoMes + " reaberta para rascunho";
    }

    private Periodo exigirPeriodo(Long ministerioId, YearMonth mes, String acao) {
        return periodos.doMes(ministerioId, mes)
                .orElseThrow(() -> RegraVioladaException.geral(
                        Datas.mesPorExtenso(mes) + " ainda não tem eventos: não há escala para " + acao + "."));
    }

    private boolean gerando(Periodo periodo) {
        return andamentos
                .doPeriodo(periodo.getId())
                .filter(Andamento::isGerando)
                .isPresent();
    }

    /** "Equilíbrio · Quem mais serve tem 4 escalas; quem menos, 1." */
    private static Item regra(TipoDeRegra tipo, long penalidade, EscalaDoPeriodo escala) {
        String detalhe = switch (tipo) {
            case PRIORIDADE_POR_DATA -> {
                long vazias = escala.getVagas().stream()
                        .filter(vaga -> vaga.getPessoa() == null)
                        .count();
                yield vazias + (vazias == 1 ? " vaga vazia" : " vagas vazias") + ", as mais próximas pesam mais.";
            }
            case EQUILIBRIO_DE_CARGA -> {
                var cargas = escala.getPessoas().stream()
                        .map(pessoa -> escala.getVagas().stream()
                                .filter(vaga -> pessoa.equals(vaga.getPessoa()))
                                .map(vaga -> vaga.getEvento().id())
                                .distinct()
                                .count())
                        .toList();
                long maior = cargas.stream().mapToLong(Long::longValue).max().orElse(0);
                long menor = cargas.stream().mapToLong(Long::longValue).min().orElse(0);
                yield "Quem mais serve tem " + escalas(maior) + " por vir; quem menos, " + menor + ".";
            }
            default -> "Penalidade " + penalidade + ".";
        };
        return new Item(tipo.rotulo() + " · " + tipo.name(), detalhe);
    }

    private static String escalas(long quantas) {
        return quantas + (quantas == 1 ? " escala" : " escalas");
    }

    /** "LIMITE_POR_PERIODO · ", para vir antes da justificativa. */
    private static String regras(List<Violacao> violacoes) {
        if (violacoes.isEmpty()) {
            return "";
        }
        return violacoes.stream().map(violacao -> violacao.regra().name()).collect(Collectors.joining(", ")) + " · ";
    }

    /** "Transmissão, 12/10 · Dom · 18h00 · Culto de domingo". */
    private static String onde(VagaPlanejada vaga) {
        return vaga.getFuncao().nome() + ", " + AjusteDaEscala.quando(vaga.getInicio()) + " · "
                + vaga.getEvento().nome();
    }

    private static String nomeDoMes(YearMonth mes) {
        return Datas.nomeDoMes(mes).toLowerCase(Locale.ROOT);
    }
}
