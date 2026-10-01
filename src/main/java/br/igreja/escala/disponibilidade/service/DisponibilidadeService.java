package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mudanças na disponibilidade: as respostas de cada pessoa e a trava do mês, que o gerente liga e desliga.
 *
 * <p>Toda gravação de resposta lê a trava com a linha do período bloqueada ({@link PeriodoService#bloquearParaAlterar}),
 * na mesma transação: uma resposta nunca entra depois da trava, nem quando as duas chegam juntas. O bloqueio também
 * enfileira o toque duplo, que encontra a linha gravada pelo primeiro e não faz nada.
 */
@Service
public class DisponibilidadeService {

    private final DisponibilidadeRepository disponibilidades;
    private final EventoService eventos;
    private final PeriodoService periodos;
    private final MembroService membros;
    private final MinisterioService ministerios;
    private final AuditoriaService auditoria;
    private final Clock relogio;

    DisponibilidadeService(
            DisponibilidadeRepository disponibilidades,
            EventoService eventos,
            PeriodoService periodos,
            MembroService membros,
            MinisterioService ministerios,
            AuditoriaService auditoria,
            Clock relogio) {
        this.disponibilidades = disponibilidades;
        this.eventos = eventos;
        this.periodos = periodos;
        this.membros = membros;
        this.ministerios = ministerios;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /**
     * A pessoa marca a própria disponibilidade num evento de um ministério em que serve.
     *
     * @return se mudou alguma coisa; falso no toque duplo
     * @throws NaoEncontradoException se a pessoa não serve no ministério ou o evento é de outro ministério
     * @throws RegraVioladaException se a resposta é "Prefiro não", se o evento já começou ou foi cancelado, ou se a
     *     disponibilidade do mês está travada
     */
    @Transactional
    public boolean marcar(Long usuarioId, Long ministerioId, Long eventoId, Resposta resposta) {
        membros.buscarQueServe(ministerioId, usuarioId);
        var evento = eventoParaMarcar(ministerioId, eventoId, resposta);
        var periodo = periodos.bloquearParaAlterar(evento.getPeriodo().getId());
        if (periodo.isDisponibilidadeTravada()) {
            throw RegraVioladaException.geral("Sua resposta para " + descrever(evento) + " não mudou: o gerente"
                    + " travou a disponibilidade de "
                    + Datas.nomeDoMes(periodo.getMes()).toLowerCase(Locale.ROOT)
                    + ".");
        }
        return gravar(usuarioId, evento, resposta, usuarioId);
    }

    /**
     * O gerente marca em nome de quem serve no ministério, inclusive com o período travado. Toda mudança fica na
     * auditoria; a mesma resposta não muda nada nem registra.
     *
     * @return se mudou alguma coisa
     * @throws NaoEncontradoException se a pessoa não serve no ministério ou o evento é de outro ministério
     * @throws RegraVioladaException se a resposta é "Prefiro não" ou se o evento já começou ou foi cancelado
     */
    @Transactional
    public boolean marcarPeloGerente(
            Long ministerioId, Long usuarioId, Long eventoId, Resposta resposta, UsuarioAutenticado autor) {
        var pessoa = membros.buscarQueServe(ministerioId, usuarioId);
        var evento = eventoParaMarcar(ministerioId, eventoId, resposta);
        var periodo = periodos.bloquearParaAlterar(evento.getPeriodo().getId());
        boolean mudou = gravar(usuarioId, evento, resposta, autor.getId());
        if (mudou) {
            String descricao = resposta.rotulo() + " para " + pessoa.nome() + " em " + descrever(evento)
                    + (periodo.isDisponibilidadeTravada() ? ", com a disponibilidade travada" : "") + " ("
                    + ministerios.buscar(ministerioId).getNome() + ").";
            auditoria.registrar(new RegistroDeAuditoria(
                    AcaoAuditada.MARCAR_DISPONIBILIDADE, autor.getId(), ministerioId, usuarioId, descricao));
        }
        return mudou;
    }

    /**
     * Trava a disponibilidade do mês: o membro não muda mais as respostas, e o gerente ainda pode marcar em nome dele.
     * Travar de novo não faz nada nem registra outra vez.
     *
     * @return se travou agora; falso se já estava travada
     * @throws RegraVioladaException se o mês ainda não tem eventos
     */
    @Transactional
    public boolean travar(Long ministerioId, YearMonth mes, UsuarioAutenticado autor) {
        var ministerio = ministerios.buscar(ministerioId);
        boolean travou = periodos.travarDisponibilidade(ministerioId, mes);
        if (travou) {
            registrarTrava(AcaoAuditada.TRAVAR_DISPONIBILIDADE, autor, ministerio, mes, "travada");
        }
        return travou;
    }

    /**
     * @return se destravou agora; falso se já estava aberta
     * @throws RegraVioladaException se o mês ainda não tem eventos
     */
    @Transactional
    public boolean destravar(Long ministerioId, YearMonth mes, UsuarioAutenticado autor) {
        var ministerio = ministerios.buscar(ministerioId);
        boolean destravou = periodos.destravarDisponibilidade(ministerioId, mes);
        if (destravou) {
            registrarTrava(AcaoAuditada.DESTRAVAR_DISPONIBILIDADE, autor, ministerio, mes, "destravada");
        }
        return destravou;
    }

    /** O evento do ministério, se ainda dá para marcar nele. A resposta é conferida antes de tocar no período. */
    private Evento eventoParaMarcar(Long ministerioId, Long eventoId, Resposta resposta) {
        var evento = eventos.buscar(ministerioId, eventoId);
        if (resposta == Resposta.PREFERE_NAO) {
            throw RegraVioladaException.geral("Sua resposta para " + descrever(evento)
                    + " não mudou: \"Prefiro não\" ainda não vale. Marque Pode ou Não pode.");
        }
        if (evento.isCancelado()) {
            throw RegraVioladaException.geral(descrever(evento) + " foi cancelado: não dá mais para marcar.");
        }
        if (!evento.getInicio().isAfter(LocalDateTime.now(relogio))) {
            throw RegraVioladaException.geral(descrever(evento) + " já começou: não dá mais para marcar.");
        }
        return evento;
    }

    /** Upsert: uma linha por pessoa e evento. A mesma resposta não muda nada. */
    private boolean gravar(Long usuarioId, Evento evento, Resposta resposta, Long autorId) {
        var existente = disponibilidades.findByUsuarioIdAndEventoId(usuarioId, evento.getId());
        if (existente.isPresent()) {
            return existente.get().marcar(resposta, evento, autorId);
        }
        disponibilidades.save(new Disponibilidade(usuarioId, evento, resposta, autorId));
        return true;
    }

    /** "01/11 · Culto de domingo". */
    private static String descrever(Evento evento) {
        return Datas.diaEMes(evento.getData()) + " · " + evento.getNome();
    }

    /** "Disponibilidade de Novembro 2026 travada (Mídia)." */
    private void registrarTrava(
            AcaoAuditada acao, UsuarioAutenticado autor, Ministerio ministerio, YearMonth mes, String estado) {
        String descricao =
                "Disponibilidade de " + Datas.mesPorExtenso(mes) + " " + estado + " (" + ministerio.getNome() + ").";
        auditoria.registrar(new RegistroDeAuditoria(acao, autor.getId(), ministerio.getId(), null, descricao));
    }
}
