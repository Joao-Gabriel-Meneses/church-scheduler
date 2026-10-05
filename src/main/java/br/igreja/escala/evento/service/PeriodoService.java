package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.PeriodoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Os meses de cada ministério. Serviço público: a disponibilidade e a escala partem do período. */
@Service
public class PeriodoService {

    private final PeriodoRepository periodos;
    private final EntityManager entityManager;

    PeriodoService(PeriodoRepository periodos, EntityManager entityManager) {
        this.periodos = periodos;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Optional<Periodo> doMes(Long ministerioId, YearMonth mes) {
        return periodos.findByMinisterioIdAndAnoAndMes(ministerioId, mes.getYear(), mes.getMonthValue());
    }

    /** O período do mês, criado agora se o mês ainda não tinha nenhum evento. */
    @Transactional
    public Periodo obterOuCriar(Long ministerioId, YearMonth mes) {
        return doMes(ministerioId, mes).orElseGet(() -> periodos.save(new Periodo(ministerioId, mes)));
    }

    /**
     * O período relido do banco com a linha bloqueada ({@code SELECT … FOR UPDATE}) até o fim da transação de quem
     * chama. Travar a disponibilidade e gravar uma resposta passam por aqui, então as duas se enfileiram: quem grava lê
     * a trava como ela está no banco, nunca uma cópia velha.
     *
     * @throws NaoEncontradoException se o período não existe
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Periodo bloquearParaAlterar(Long periodoId) {
        var periodo =
                periodos.findById(periodoId).orElseThrow(() -> new NaoEncontradoException("Período " + periodoId));
        return bloquear(periodo);
    }

    /**
     * @return se travou agora; falso se já estava travada
     * @throws RegraVioladaException se o mês ainda não tem período (nenhum evento)
     */
    @Transactional
    public boolean travarDisponibilidade(Long ministerioId, YearMonth mes) {
        return bloquearDoMes(ministerioId, mes, "travar").travarDisponibilidade();
    }

    /**
     * @return se destravou agora; falso se já estava aberta
     * @throws RegraVioladaException se o mês ainda não tem período (nenhum evento)
     */
    @Transactional
    public boolean destravarDisponibilidade(Long ministerioId, YearMonth mes) {
        return bloquearDoMes(ministerioId, mes, "destravar").destravarDisponibilidade();
    }

    /**
     * Publica a escala do período, com a linha bloqueada (a geração e o ajuste também bloqueiam).
     *
     * @return se publicou agora; falso se já estava publicada
     * @throws NaoEncontradoException se o período não existe
     */
    @Transactional
    public boolean publicarEscala(Long periodoId) {
        return bloquearParaAlterar(periodoId).publicarEscala();
    }

    /**
     * Volta a escala do período para rascunho, com a linha bloqueada.
     *
     * @return se reabriu agora; falso se já era rascunho
     * @throws NaoEncontradoException se o período não existe
     */
    @Transactional
    public boolean reabrirEscala(Long periodoId) {
        return bloquearParaAlterar(periodoId).reabrirEscala();
    }

    private Periodo bloquearDoMes(Long ministerioId, YearMonth mes, String acao) {
        var periodo = doMes(ministerioId, mes)
                .orElseThrow(() -> RegraVioladaException.geral(Datas.mesPorExtenso(mes)
                        + " ainda não tem eventos: não há disponibilidade para " + acao + "."));
        return bloquear(periodo);
    }

    /** Relê o estado junto com o bloqueio: um {@code find} com lock não atualizaria uma entidade já carregada. */
    private Periodo bloquear(Periodo periodo) {
        entityManager.refresh(periodo, LockModeType.PESSIMISTIC_WRITE);
        return periodo;
    }
}
