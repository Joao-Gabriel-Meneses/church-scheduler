package br.igreja.escala.escala.service;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.EdicaoConcorrenteException;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.solver.DiagnosticoDaVaga;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.Pessoa;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.escala.solver.ValidacaoDaVaga;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.domain.Funcao;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O ajuste manual da escala pelo gerente: trocar, preencher, esvaziar, fixar e forçar uma vaga, salvando na hora, em
 * rascunho ou publicada. Toda alteração fixa a vaga e vai para a auditoria com o antes e o depois. Quem decide se a
 * pessoa cabe é o solver (ValidacaoDaVaga): regra que nunca se força recusa; regra forçável pede justificativa.
 *
 * <p>Cada alteração bloqueia o período (como a geração e a trava da disponibilidade) e confere a versão que a tela viu:
 * duas abas mexendo na mesma vaga, a segunda é recusada.
 */
@Service
public class AjusteDaEscala {

    private final LeituraDoPeriodo leitura;
    private final VagaRepository vagas;
    private final EventoService eventos;
    private final PeriodoService periodos;
    private final GeracoesEmAndamento andamentos;
    private final SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager;
    private final AuditoriaService auditoria;
    private final EntityManager entityManager;

    @Autowired
    AjusteDaEscala(
            LeituraDoPeriodo leitura,
            VagaRepository vagas,
            EventoService eventos,
            PeriodoService periodos,
            GeracoesEmAndamento andamentos,
            SolverManager<EscalaDoPeriodo> solverManager,
            AuditoriaService auditoria,
            EntityManager entityManager) {
        this(
                leitura,
                vagas,
                eventos,
                periodos,
                andamentos,
                SolutionManager.create(solverManager),
                auditoria,
                entityManager);
    }

    AjusteDaEscala(
            LeituraDoPeriodo leitura,
            VagaRepository vagas,
            EventoService eventos,
            PeriodoService periodos,
            GeracoesEmAndamento andamentos,
            SolutionManager<EscalaDoPeriodo, HardMediumSoftScore> solutionManager,
            AuditoriaService auditoria,
            EntityManager entityManager) {
        this.leitura = leitura;
        this.vagas = vagas;
        this.eventos = eventos;
        this.periodos = periodos;
        this.andamentos = andamentos;
        this.solutionManager = solutionManager;
        this.auditoria = auditoria;
        this.entityManager = entityManager;
    }

    /**
     * A vaga com os candidatos da função para o gerente escolher, quem passa em tudo primeiro.
     *
     * @throws NaoEncontradoException se a vaga não existe ou é de outro ministério
     */
    @Transactional(readOnly = true)
    public VagaEmAjuste abrir(Long ministerioId, Long vagaId) {
        var vaga = vagas.findById(vagaId).orElseThrow(() -> new NaoEncontradoException("Vaga " + vagaId));
        var evento = eventos.buscar(ministerioId, vaga.getEventoId());
        var edicao = ler(ministerioId, vaga, evento);
        var funcao = edicao.funcao();
        String quando = quando(evento.getInicio());
        var planejada = edicao.planejada();
        String bloqueio = bloqueio(edicao);
        if (planejada == null) {
            return new VagaEmAjuste(
                    ministerioId,
                    vagaId,
                    vaga.getVersao(),
                    edicao.mes(),
                    funcao == null ? "Função excluída" : funcao.getNome(),
                    quando,
                    evento.getNome(),
                    vaga.isVazia() ? null : nome(edicao, vaga.getUsuarioId()),
                    vaga.isFixada(),
                    vaga.isForcada(),
                    vaga.getJustificativa(),
                    List.of(),
                    null,
                    bloqueio,
                    List.of());
        }
        var validacao = edicao.validacao();
        var candidatos = bloqueio != null
                ? List.<Candidato>of()
                : edicao.escala().getPessoas().stream()
                        .filter(pessoa -> pessoa.habilitadaEm(funcao.getId()))
                        .map(pessoa -> candidato(edicao, planejada, pessoa, validacao.aoEscalar(planejada, pessoa)))
                        .sorted(Comparator.comparingInt(AjusteDaEscala::grupo)
                                .thenComparingLong(Candidato::escalasNoMes)
                                .thenComparing(Candidato::nome, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        String motivoDaVazia = planejada.getPessoa() == null
                ? new DiagnosticoDaVaga(
                                edicao.escala(),
                                edicao.dados().regras(),
                                edicao.dados().nomesDosNiveis())
                        .motivo(planejada)
                        .texto()
                : null;
        return new VagaEmAjuste(
                ministerioId,
                vagaId,
                vaga.getVersao(),
                edicao.mes(),
                funcao.getNome(),
                quando,
                evento.getNome(),
                vaga.isVazia() ? null : nome(edicao, vaga.getUsuarioId()),
                vaga.isFixada(),
                vaga.isForcada(),
                vaga.getJustificativa(),
                validacao.avisos().getOrDefault(vagaId, List.of()),
                motivoDaVazia,
                bloqueio,
                candidatos);
    }

    /**
     * Põe a pessoa na vaga e fixa. Se ela viola só regras forçáveis, a vaga fica forçada, com a justificativa.
     *
     * @return o texto do aviso de sucesso
     * @throws RegraVioladaException se viola regra que nunca se força, ou forçável sem justificativa (campo
     *     {@code justificativa}), ou se a vaga não se ajusta agora
     * @throws EdicaoConcorrenteException se a vaga mudou depois que a tela a leu
     * @throws NaoEncontradoException se a vaga é de outro ministério ou a pessoa não serve nele
     */
    @Transactional
    public String escalar(
            Long ministerioId,
            Long vagaId,
            Long usuarioId,
            String justificativa,
            long versao,
            UsuarioAutenticado autor) {
        var edicao = travar(ministerioId, vagaId, versao);
        var pessoa = edicao.escala().getPessoas().stream()
                .filter(candidata -> candidata.id().equals(usuarioId))
                .findFirst()
                .orElseThrow(
                        () -> new NaoEncontradoException("Pessoa " + usuarioId + " no ministério " + ministerioId));
        var violacoes = edicao.validacao().aoEscalar(edicao.planejada(), pessoa);
        var vaga = edicao.vaga();
        String antes = nome(edicao, vaga.getUsuarioId());
        var naoForcaveis =
                violacoes.stream().filter(violacao -> !violacao.forcavel()).toList();
        if (!naoForcaveis.isEmpty()) {
            throw RegraVioladaException.geral(pessoa.nome() + " não pode servir em " + edicao.onde() + ". "
                    + porQue(naoForcaveis) + " " + regras(naoForcaveis));
        }
        boolean forcar = !violacoes.isEmpty();
        if (forcar) {
            if (justificativa == null || justificativa.isBlank()) {
                throw new RegraVioladaException(
                        "justificativa",
                        "Para escalar " + pessoa.nome() + " mesmo assim, escreva a justificativa. " + porQue(violacoes)
                                + " " + regras(violacoes));
            }
            if (justificativa.strip().length() > Vaga.TAMANHO_JUSTIFICATIVA) {
                throw new RegraVioladaException(
                        "justificativa",
                        "A justificativa tem no máximo " + Vaga.TAMANHO_JUSTIFICATIVA + " caracteres.");
            }
            vaga.forcar(pessoa.id(), justificativa);
        } else {
            vaga.ajustar(pessoa.id());
        }
        registrar(
                edicao,
                forcar ? AcaoAuditada.FORCAR_VAGA : AcaoAuditada.AJUSTAR_VAGA,
                autor,
                pessoa.id(),
                antes + " → " + pessoa.nome() + "."
                        + (forcar
                                ? " Forçada ("
                                        + violacoes.stream()
                                                .map(violacao ->
                                                        violacao.regra().name())
                                                .collect(Collectors.joining(", "))
                                        + "): " + vaga.getJustificativa()
                                : ""));
        return pessoa.nome() + " está em " + edicao.ondeCurto() + (forcar ? ", com a vaga forçada" : "");
    }

    /**
     * Esvazia a vaga e fixa vazia (gerar de novo não a preenche).
     *
     * @return o texto do aviso de sucesso
     * @throws RegraVioladaException se tirar a pessoa piora uma regra rígida (ex.: o único Experiente do evento)
     */
    @Transactional
    public String esvaziar(Long ministerioId, Long vagaId, long versao, UsuarioAutenticado autor) {
        var edicao = travar(ministerioId, vagaId, versao);
        var vaga = edicao.vaga();
        var violacoes = edicao.validacao().aoEsvaziar(edicao.planejada());
        if (!violacoes.isEmpty()) {
            throw RegraVioladaException.geral("A vaga de " + edicao.onde() + " não pode ficar vazia agora. "
                    + porQue(violacoes) + " " + regras(violacoes));
        }
        Long saiu = vaga.getUsuarioId();
        String antes = nome(edicao, saiu);
        vaga.ajustar(null);
        registrar(edicao, AcaoAuditada.AJUSTAR_VAGA, autor, saiu, antes + " → vazia.");
        return "Vaga de " + edicao.ondeCurto() + " esvaziada";
    }

    /** Fixa a vaga como está: gerar de novo não a muda. */
    @Transactional
    public String fixar(Long ministerioId, Long vagaId, long versao, UsuarioAutenticado autor) {
        var edicao = travar(ministerioId, vagaId, versao);
        var vaga = edicao.vaga();
        if (!vaga.isFixada()) {
            vaga.fixar();
            registrar(
                    edicao,
                    AcaoAuditada.FIXAR_VAGA,
                    autor,
                    vaga.getUsuarioId(),
                    fixadaOuSolta("fixada", edicao, vaga) + ".");
        }
        return "Vaga de " + edicao.ondeCurto() + " fixada";
    }

    /** Solta a vaga para a próxima geração; a forçada deixa de ser forçada. */
    @Transactional
    public String desafixar(Long ministerioId, Long vagaId, long versao, UsuarioAutenticado autor) {
        var edicao = travar(ministerioId, vagaId, versao);
        var vaga = edicao.vaga();
        if (vaga.isPresa()) {
            boolean eraForcada = vaga.isForcada();
            vaga.desafixar();
            registrar(
                    edicao,
                    AcaoAuditada.DESAFIXAR_VAGA,
                    autor,
                    vaga.getUsuarioId(),
                    fixadaOuSolta("solta", edicao, vaga) + (eraForcada ? " (deixou de ser forçada)" : "") + ".");
        }
        return "Vaga de " + edicao.ondeCurto() + " solta para a próxima geração";
    }

    /**
     * A vaga do ministério com o período bloqueado e relida, pronta para mudar.
     *
     * @throws RegraVioladaException se a escala está sendo gerada ou a vaga não se ajusta mais
     * @throws EdicaoConcorrenteException se a versão não é a que a tela viu
     */
    private Edicao travar(Long ministerioId, Long vagaId, long versao) {
        var vaga = vagas.findById(vagaId).orElseThrow(() -> new NaoEncontradoException("Vaga " + vagaId));
        var evento = eventos.buscar(ministerioId, vaga.getEventoId());
        var mes = YearMonth.from(evento.getData());
        var periodo = periodos.doMes(ministerioId, mes)
                .orElseThrow(() -> new NaoEncontradoException("Período de " + mes + " no ministério " + ministerioId));
        periodos.bloquearParaAlterar(periodo.getId());
        entityManager.refresh(vaga);
        if (vaga.getVersao() != versao) {
            throw new EdicaoConcorrenteException("A vaga mudou em outra aba ou por outra pessoa depois que você abriu"
                    + " a escala. A grade foi atualizada: confira e tente de novo.");
        }
        var edicao = ler(ministerioId, vaga, evento);
        String bloqueio = bloqueio(edicao);
        if (bloqueio != null) {
            throw RegraVioladaException.geral(bloqueio);
        }
        return edicao;
    }

    private Edicao ler(Long ministerioId, Vaga vaga, Evento evento) {
        var mes = YearMonth.from(evento.getData());
        var dados = leitura.ler(ministerioId, mes);
        var escala = MontagemDaEscala.montar(dados, false);
        var planejada = escala.getVagas().stream()
                .filter(candidata -> candidata.getId().equals(vaga.getId()))
                .findFirst()
                .orElse(null);
        var funcao = dados.funcoes().stream()
                .filter(candidata -> candidata.getId().equals(vaga.getFuncaoId()))
                .findFirst()
                .orElse(null);
        var gerando = dados.periodo() != null
                && andamentos
                        .doPeriodo(dados.periodo().getId())
                        .filter(Andamento::isGerando)
                        .isPresent();
        return new Edicao(
                dados,
                escala,
                vaga,
                evento,
                funcao,
                planejada,
                new ValidacaoDaVaga(solutionManager, escala, dados.regras(), dados.nomesDosNiveis()),
                gerando);
    }

    private static String bloqueio(Edicao edicao) {
        String nomeDoMes = Datas.nomeDoMes(edicao.mes()).toLowerCase(Locale.ROOT);
        if (edicao.gerando()) {
            return "A escala de " + nomeDoMes + " está sendo gerada. Espere terminar para ajustar as vagas.";
        }
        if (edicao.planejada() == null) {
            return "Esta vaga não se ajusta mais: o evento já começou, foi cancelado ou não precisa mais desta função.";
        }
        return null;
    }

    private void registrar(Edicao edicao, AcaoAuditada acao, UsuarioAutenticado autor, Long alvo, String mudanca) {
        boolean publicada = edicao.dados().periodo().isEscalaPublicada();
        auditoria.registrar(new RegistroDeAuditoria(
                acao,
                autor.getId(),
                edicao.dados().ministerioId(),
                alvo,
                edicao.onde() + " (" + edicao.dados().nomeDoMinisterio() + (publicada ? ", escala publicada" : "")
                        + "): " + mudanca));
    }

    private static Candidato candidato(
            Edicao edicao, VagaPlanejada planejada, Pessoa pessoa, List<Violacao> violacoes) {
        Long nivel = pessoa.nivelEm(planejada.getFuncao().id());
        return new Candidato(
                pessoa.id(),
                pessoa.nome(),
                SlotDaGrade.iniciais(pessoa.nome()),
                nivel == null ? null : edicao.dados().nomesDosNiveis().get(nivel),
                escalasNoMes(edicao.escala(), planejada, pessoa),
                violacoes,
                pessoa.equals(planejada.getPessoa()));
    }

    /** Em quantos eventos do mês a pessoa serve, sem contar esta vaga (inclui os que já começaram). */
    private static long escalasNoMes(EscalaDoPeriodo escala, VagaPlanejada vaga, Pessoa pessoa) {
        var nasVagas = escala.getVagas().stream()
                .filter(outra -> outra != vaga && pessoa.equals(outra.getPessoa()))
                .map(outra -> outra.getEvento().id());
        var nosCompromissos = escala.getCompromissos().stream()
                .filter(compromisso ->
                        compromisso.contaNoPeriodo() && compromisso.pessoaId().equals(pessoa.id()))
                .map(compromisso -> compromisso.eventoId());
        return Stream.concat(nasVagas, nosCompromissos)
                .filter(evento -> !evento.equals(vaga.getEvento().id()))
                .distinct()
                .count();
    }

    /** Livre primeiro, depois forçável, depois bloqueado. */
    private static int grupo(Candidato candidato) {
        return candidato.isLivre() ? 0 : (candidato.isForcavel() ? 1 : 2);
    }

    private static String nome(Edicao edicao, Long usuarioId) {
        if (usuarioId == null) {
            return "vazia";
        }
        return Stream.concat(edicao.dados().quemServe().stream(), edicao.dados().quemNaoServeMais().stream())
                .filter(pessoa -> pessoa.id().equals(usuarioId))
                .map(UsuarioResumo::nome)
                .findFirst()
                .orElse("Pessoa removida");
    }

    /** "vaga fixada com Ana Souza", "vaga solta vazia". */
    private static String fixadaOuSolta(String estado, Edicao edicao, Vaga vaga) {
        return "vaga " + estado + (vaga.isVazia() ? " vazia" : " com " + nome(edicao, vaga.getUsuarioId()));
    }

    private static String porQue(List<Violacao> violacoes) {
        return violacoes.stream().map(Violacao::porQue).collect(Collectors.joining(" "));
    }

    private static String regras(List<Violacao> violacoes) {
        return (violacoes.size() == 1 ? "Regra: " : "Regras: ")
                + violacoes.stream().map(violacao -> violacao.regra().name()).collect(Collectors.joining(" · "));
    }

    /** "12/10 · Dom · 18h00". */
    static String quando(LocalDateTime inicio) {
        return Datas.dataCurta(inicio.toLocalDate()) + " · " + Datas.horario(inicio.toLocalTime());
    }

    /** O que uma alteração lê: o mês, a vaga relida e a validação sobre a escala como está. */
    private record Edicao(
            DadosDoPeriodo dados,
            EscalaDoPeriodo escala,
            Vaga vaga,
            Evento evento,
            Funcao funcao,
            VagaPlanejada planejada,
            ValidacaoDaVaga validacao,
            boolean gerando) {

        YearMonth mes() {
            return dados.mes();
        }

        /** "Projeção, 12/10 · Dom · 18h00 · Culto de domingo". */
        String onde() {
            return Objects.requireNonNull(funcao).getNome() + ", " + quando(evento.getInicio()) + " · "
                    + evento.getNome();
        }

        /** "Projeção, 12/10 · Dom". */
        String ondeCurto() {
            return funcao.getNome() + ", " + Datas.dataCurta(evento.getData());
        }
    }
}
