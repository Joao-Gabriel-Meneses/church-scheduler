package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mudanças na disponibilidade: a trava do mês, que o gerente liga e desliga. */
@Service
public class DisponibilidadeService {

    private final PeriodoService periodos;
    private final MinisterioService ministerios;
    private final AuditoriaService auditoria;

    DisponibilidadeService(PeriodoService periodos, MinisterioService ministerios, AuditoriaService auditoria) {
        this.periodos = periodos;
        this.ministerios = ministerios;
        this.auditoria = auditoria;
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
            registrar(AcaoAuditada.TRAVAR_DISPONIBILIDADE, autor, ministerio, mes, "travada");
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
            registrar(AcaoAuditada.DESTRAVAR_DISPONIBILIDADE, autor, ministerio, mes, "destravada");
        }
        return destravou;
    }

    /** "Disponibilidade de Novembro 2026 travada (Mídia)." */
    private void registrar(
            AcaoAuditada acao, UsuarioAutenticado autor, Ministerio ministerio, YearMonth mes, String estado) {
        String descricao =
                "Disponibilidade de " + Datas.mesPorExtenso(mes) + " " + estado + " (" + ministerio.getNome() + ").";
        auditoria.registrar(new RegistroDeAuditoria(acao, autor.getId(), ministerio.getId(), null, descricao));
    }
}
