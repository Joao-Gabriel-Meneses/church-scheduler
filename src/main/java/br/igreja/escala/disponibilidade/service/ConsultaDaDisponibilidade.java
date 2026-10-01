package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.MembroService;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    private final UsuarioService usuarios;

    ConsultaDaDisponibilidade(
            DisponibilidadeRepository disponibilidades,
            EventoService eventos,
            PeriodoService periodos,
            MembroService membros,
            UsuarioService usuarios) {
        this.disponibilidades = disponibilidades;
        this.eventos = eventos;
        this.periodos = periodos;
        this.membros = membros;
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
