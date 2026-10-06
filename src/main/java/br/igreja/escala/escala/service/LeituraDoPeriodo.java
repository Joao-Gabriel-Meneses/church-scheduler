package br.igreja.escala.escala.service;

import br.igreja.escala.disponibilidade.service.ConsultaDaDisponibilidade;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.solver.CompromissoFixo;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.HabilitacaoService;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lê, pelos serviços públicos dos outros módulos, o que a escala de um mês precisa (DadosDoPeriodo). */
@Component
class LeituraDoPeriodo {

    private final MinisterioService ministerios;
    private final PeriodoService periodos;
    private final EventoService eventos;
    private final FuncaoService funcoes;
    private final NivelService niveis;
    private final MembroService membros;
    private final HabilitacaoService habilitacoes;
    private final UsuarioService usuarios;
    private final ConsultaDaDisponibilidade disponibilidade;
    private final RegraService regras;
    private final VagaRepository vagas;
    private final Clock relogio;

    LeituraDoPeriodo(
            MinisterioService ministerios,
            PeriodoService periodos,
            EventoService eventos,
            FuncaoService funcoes,
            NivelService niveis,
            MembroService membros,
            HabilitacaoService habilitacoes,
            UsuarioService usuarios,
            ConsultaDaDisponibilidade disponibilidade,
            RegraService regras,
            VagaRepository vagas,
            Clock relogio) {
        this.ministerios = ministerios;
        this.periodos = periodos;
        this.eventos = eventos;
        this.funcoes = funcoes;
        this.niveis = niveis;
        this.membros = membros;
        this.habilitacoes = habilitacoes;
        this.usuarios = usuarios;
        this.disponibilidade = disponibilidade;
        this.regras = regras;
        this.vagas = vagas;
        this.relogio = relogio;
    }

    /**
     * @throws br.igreja.escala.compartilhado.NaoEncontradoException se o ministério não existe
     */
    @Transactional(readOnly = true)
    public DadosDoPeriodo ler(Long ministerioId, YearMonth mes) {
        var ministerio = ministerios.buscar(ministerioId);
        var doMes = eventos.daEscala(ministerioId, mes);
        List<Vaga> vagasDoMes = doMes.isEmpty()
                ? List.of()
                : vagas.findByEventoIdIn(doMes.stream().map(Evento::getId).toList());
        var quemServe = membros.queServem(ministerioId);
        Set<Long> servem = quemServe.stream().map(UsuarioResumo::id).collect(Collectors.toSet());
        Set<Long> naoServem = vagasDoMes.stream()
                .map(Vaga::getUsuarioId)
                .filter(id -> id != null && !servem.contains(id))
                .collect(Collectors.toSet());
        Set<Long> desistiram = vagasDoMes.stream()
                .map(Vaga::getDesistenteId)
                .filter(id -> id != null && !servem.contains(id) && !naoServem.contains(id))
                .collect(Collectors.toSet());
        return new DadosDoPeriodo(
                ministerioId,
                ministerio.getNome(),
                mes,
                periodos.doMes(ministerioId, mes).orElse(null),
                doMes,
                funcoes.listar(ministerioId),
                vagasDoMes,
                quemServe,
                naoServem.isEmpty() ? List.of() : usuarios.resumos(naoServem),
                disponibilidade.quemPode(ministerioId, mes),
                habilitacoes.doMinisterio(ministerioId),
                niveis.listar(ministerioId).stream().collect(Collectors.toMap(Nivel::getId, Nivel::getNome)),
                regras.doMinisterio(ministerioId),
                emOutrosMinisterios(ministerioId, mes, servem),
                LocalDateTime.now(relogio),
                desistiram.isEmpty() ? List.of() : usuarios.resumos(desistiram));
    }

    /**
     * As vagas vigentes de outros ministérios, de quem serve aqui, em eventos do mês (e do dia antes e do dia depois,
     * que podem cruzar a meia-noite). Só pessoa e horário: nada do outro ministério vai para a tela.
     */
    private List<CompromissoFixo> emOutrosMinisterios(Long ministerioId, YearMonth mes, Set<Long> servem) {
        if (servem.isEmpty()) {
            return List.of();
        }
        Map<Long, Evento> deOutros = eventos
                .deOutrosMinisteriosEntre(
                        ministerioId,
                        mes.atDay(1).minusDays(1),
                        mes.atEndOfMonth().plusDays(1))
                .stream()
                .collect(Collectors.toMap(Evento::getId, Function.identity()));
        if (deOutros.isEmpty()) {
            return List.of();
        }
        var preenchidas = vagas.findByEventoIdInAndUsuarioIdIsNotNull(deOutros.keySet()).stream()
                .filter(vaga -> servem.contains(vaga.getUsuarioId()))
                .toList();
        if (preenchidas.isEmpty()) {
            return List.of();
        }
        Map<Long, Funcao> funcoesDeOutros =
                funcoes.porIds(preenchidas.stream().map(Vaga::getFuncaoId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Funcao::getId, Function.identity()));
        return preenchidas.stream()
                .filter(vaga -> MontagemDaEscala.vigente(
                        vaga, deOutros.get(vaga.getEventoId()), funcoesDeOutros.get(vaga.getFuncaoId())))
                .map(vaga -> {
                    var evento = deOutros.get(vaga.getEventoId());
                    return new CompromissoFixo(
                            vaga.getUsuarioId(), evento.getId(), evento.getInicio(), evento.getFim(), false);
                })
                .toList();
    }
}
