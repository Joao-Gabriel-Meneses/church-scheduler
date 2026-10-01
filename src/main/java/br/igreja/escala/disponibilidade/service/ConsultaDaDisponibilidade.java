package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que as telas de disponibilidade mostram. Só entram os eventos por vir (o que já começou ou foi cancelado some) e
 * só quem serve no ministério; um evento sem linha de resposta aparece como "sem resposta".
 */
@Service
public class ConsultaDaDisponibilidade {

    private final DisponibilidadeRepository disponibilidades;
    private final EventoService eventos;
    private final PeriodoService periodos;
    private final MembroService membros;
    private final MinisterioService ministerios;
    private final UsuarioService usuarios;

    ConsultaDaDisponibilidade(
            DisponibilidadeRepository disponibilidades,
            EventoService eventos,
            PeriodoService periodos,
            MembroService membros,
            MinisterioService ministerios,
            UsuarioService usuarios) {
        this.disponibilidades = disponibilidades;
        this.eventos = eventos;
        this.periodos = periodos;
        this.membros = membros;
        this.ministerios = ministerios;
        this.usuarios = usuarios;
    }

    /** A tela da pessoa: um grupo por ministério em que ela serve, que ela marca até a trava. */
    @Transactional(readOnly = true)
    public TelaDaDisponibilidade doMembro(Long usuarioId, YearMonth mes) {
        var ministerios = membros.ministeriosEmQueServe(usuarioId);
        var grupos = ministerios.stream()
                .map(ministerio -> grupo(ministerio, usuarioId, mes, false))
                .toList();
        return new TelaDaDisponibilidade(mes, !ministerios.isEmpty(), grupos);
    }

    /**
     * O grupo de uma pessoa para o gerente marcar em nome dela: editável mesmo com o período travado.
     *
     * @throws NaoEncontradoException se a pessoa não serve no ministério
     */
    @Transactional(readOnly = true)
    public DisponibilidadeDeUmMembro peloGerente(Long ministerioId, Long usuarioId, YearMonth mes) {
        var pessoa = membros.buscarQueServe(ministerioId, usuarioId);
        return new DisponibilidadeDeUmMembro(pessoa, grupo(ministerios.buscar(ministerioId), usuarioId, mes, true));
    }

    /**
     * O painel do gerente: quem serve no ministério × os eventos por vir do mês. "Respondeu" é quem respondeu todos;
     * quem falta vem primeiro.
     */
    @Transactional(readOnly = true)
    public PainelDaDisponibilidade painel(Long ministerioId, YearMonth mes) {
        var periodo = periodos.doMes(ministerioId, mes);
        var doMes = eventos.porVirDoMes(ministerioId, mes);
        var pessoas = membros.queServem(ministerioId);
        Map<Long, Map<Long, Resposta>> porPessoa = respostasPorPessoa(doMes);
        var linhas = pessoas.stream()
                .map(pessoa -> {
                    var dela = porPessoa.getOrDefault(pessoa.id(), Map.of());
                    var respostas = new ArrayList<Resposta>();
                    doMes.forEach(evento -> respostas.add(dela.get(evento.getId())));
                    return new LinhaDoPainel(pessoa, respostas);
                })
                .sorted(Comparator.comparing(LinhaDoPainel::respondeuTudo))
                .toList();
        var colunas = doMes.stream()
                .map(evento -> new EventoDoPainel(
                        evento.getId(),
                        evento.getNome(),
                        Datas.dia(evento.getData()) + " "
                                + Datas.diaDaSemanaCurto(evento.getData().getDayOfWeek()),
                        Datas.horario(evento.getHorario()),
                        podem(evento, pessoas, porPessoa)))
                .toList();
        return new PainelDaDisponibilidade(
                mes,
                periodo.isPresent(),
                periodo.map(Periodo::isDisponibilidadeTravada).orElse(false),
                colunas,
                linhas);
    }

    /**
     * Quem pode servir em cada evento por vir do mês: só quem serve no ministério e marcou Pode. Sem resposta conta como
     * indisponível, assim como Não pode e a conta desativada. É o que a geração da escala (Fase 3) usa.
     *
     * @return por id de evento, os ids de quem pode; todo evento por vir aparece, mesmo sem ninguém
     */
    @Transactional(readOnly = true)
    public Map<Long, Set<Long>> quemPode(Long ministerioId, YearMonth mes) {
        var doMes = eventos.porVirDoMes(ministerioId, mes);
        var pessoas = membros.queServem(ministerioId);
        var porPessoa = respostasPorPessoa(doMes);
        return doMes.stream()
                .collect(Collectors.toMap(
                        Evento::getId,
                        evento -> pessoas.stream()
                                .map(UsuarioResumo::id)
                                .filter(id -> pode(porPessoa, id, evento))
                                .collect(Collectors.toSet())));
    }

    /** Respostas aos eventos, por pessoa e evento. */
    private Map<Long, Map<Long, Resposta>> respostasPorPessoa(List<Evento> doMes) {
        if (doMes.isEmpty()) {
            return Map.of();
        }
        return disponibilidades
                .findByEventoIdIn(doMes.stream().map(Evento::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        Disponibilidade::getUsuarioId,
                        Collectors.toMap(Disponibilidade::getEventoId, Disponibilidade::getResposta)));
    }

    private static long podem(Evento evento, List<UsuarioResumo> pessoas, Map<Long, Map<Long, Resposta>> porPessoa) {
        return pessoas.stream()
                .filter(pessoa -> pode(porPessoa, pessoa.id(), evento))
                .count();
    }

    private static boolean pode(Map<Long, Map<Long, Resposta>> porPessoa, Long usuarioId, Evento evento) {
        return porPessoa.getOrDefault(usuarioId, Map.of()).get(evento.getId()) == Resposta.PODE;
    }

    private GrupoDeDisponibilidade grupo(Ministerio ministerio, Long usuarioId, YearMonth mes, boolean peloGerente) {
        boolean travado = periodos.doMes(ministerio.getId(), mes)
                .map(Periodo::isDisponibilidadeTravada)
                .orElse(false);
        var doMes = eventos.porVirDoMes(ministerio.getId(), mes);
        Map<Long, Disponibilidade> respostas = respostasDe(usuarioId, doMes);
        Map<Long, String> nomes = nomesDeQuemMarcou(respostas.values());
        var linhas = doMes.stream()
                .map(evento -> {
                    var resposta = respostas.get(evento.getId());
                    return LinhaDeDisponibilidade.de(
                            evento, resposta, resposta == null ? null : nomes.get(resposta.getMarcadoPorId()));
                })
                .toList();
        return new GrupoDeDisponibilidade(
                ministerio.getId(),
                ministerio.getNome(),
                ministerio.getCor().tint(),
                mes,
                travado,
                peloGerente || !travado,
                linhas);
    }

    private Map<Long, Disponibilidade> respostasDe(Long usuarioId, List<Evento> doMes) {
        if (doMes.isEmpty()) {
            return Map.of();
        }
        return disponibilidades
                .findByUsuarioIdAndEventoIdIn(
                        usuarioId, doMes.stream().map(Evento::getId).toList())
                .stream()
                .collect(Collectors.toMap(Disponibilidade::getEventoId, Function.identity()));
    }

    private Map<Long, String> nomesDeQuemMarcou(Collection<Disponibilidade> respostas) {
        var ids = respostas.stream()
                .filter(Disponibilidade::marcadaPorOutro)
                .map(Disponibilidade::getMarcadoPorId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return usuarios.resumosPorId(ids).values().stream()
                .collect(Collectors.toMap(UsuarioResumo::id, UsuarioResumo::nome));
    }
}
