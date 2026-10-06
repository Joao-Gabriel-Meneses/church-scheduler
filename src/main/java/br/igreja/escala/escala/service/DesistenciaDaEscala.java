package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O membro desiste de uma escala publicada: a vaga fica vazia na hora, sem aprovação, e a escala do mês não é refeita.
 * O gerente vê o alerta na página de escalas e preenche pelo ajuste manual. Vale até 24 h antes do evento; depois disso,
 * só o gerente muda a vaga.
 *
 * <p>Como o ajuste e a publicação, a desistência bloqueia o período e relê a vaga: o toque duplo se enfileira, e o
 * segundo encontra a vaga já vazia com a mesma pessoa como desistente, e não muda nada.
 */
@Service
public class DesistenciaDaEscala {

    /** Até quanto antes do início o membro desiste sozinho. */
    public static final Duration PRAZO = Duration.ofHours(24);

    private final VagaRepository vagas;
    private final EventoService eventos;
    private final FuncaoService funcoes;
    private final MinisterioService ministerios;
    private final PeriodoService periodos;
    private final AuditoriaService auditoria;
    private final EntityManager entityManager;
    private final Clock relogio;

    DesistenciaDaEscala(
            VagaRepository vagas,
            EventoService eventos,
            FuncaoService funcoes,
            MinisterioService ministerios,
            PeriodoService periodos,
            AuditoriaService auditoria,
            EntityManager entityManager,
            Clock relogio) {
        this.vagas = vagas;
        this.eventos = eventos;
        this.funcoes = funcoes;
        this.ministerios = ministerios;
        this.periodos = periodos;
        this.auditoria = auditoria;
        this.entityManager = entityManager;
        this.relogio = relogio;
    }

    /** Se ainda faltam pelo menos 24 h para o início: exatamente 24 h ainda vale. */
    public static boolean noPrazo(LocalDateTime inicio, LocalDateTime agora) {
        return !agora.isAfter(inicio.minus(PRAZO));
    }

    /**
     * Tira a pessoa logada da vaga, que fica vazia e fixada, e registra na auditoria. Se ela já desistiu desta vaga e
     * ninguém entrou no lugar, não muda nada.
     *
     * @return o texto do aviso de sucesso
     * @throws NaoEncontradoException se a vaga não é da pessoa, ou não está numa escala publicada (rascunho, evento
     *     cancelado ou função que o evento não exige mais): o membro não vê essas vagas
     * @throws RegraVioladaException se o evento já começou ou faltam menos de 24 h
     */
    @Transactional
    public String desistir(Long vagaId, UsuarioAutenticado membro) {
        var vaga = vagas.findById(vagaId)
                .filter(candidata -> daPessoa(candidata, membro.getId()))
                .orElseThrow(() -> naoEncontrada(vagaId, membro));
        var evento = unico(eventos.porIds(List.of(vaga.getEventoId())));
        var funcao = unico(funcoes.porIds(List.of(vaga.getFuncaoId())));
        if (evento == null || funcao == null || !MontagemDaEscala.vigente(vaga, evento, funcao)) {
            throw naoEncontrada(vagaId, membro);
        }
        var periodo = periodos.bloquearParaAlterar(evento.getPeriodo().getId());
        entityManager.refresh(vaga);
        if (!periodo.isEscalaPublicada() || !daPessoa(vaga, membro.getId())) {
            throw naoEncontrada(vagaId, membro);
        }
        String onde = funcao.getNome() + ", " + Datas.dataCurta(evento.getData());
        if (!membro.getId().equals(vaga.getUsuarioId())) {
            return "Você já tinha desistido de " + onde;
        }
        var agora = LocalDateTime.now(relogio);
        var ministerio = ministerios.buscar(evento.getMinisterioId()).getNome();
        if (!evento.getInicio().isAfter(agora)) {
            throw RegraVioladaException.geral(evento.getNome() + " (" + quando(evento)
                    + ") já começou. Para sair da escala, fale com o gerente" + " da " + ministerio + ".");
        }
        if (!noPrazo(evento.getInicio(), agora)) {
            throw RegraVioladaException.geral("Faltam menos de 24 h para " + evento.getNome() + " (" + quando(evento)
                    + "). Para desistir agora, fale com o gerente da " + ministerio + ".");
        }
        vaga.desistir(Instant.now(relogio));
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.DESISTIR_DA_VAGA,
                membro.getId(),
                evento.getMinisterioId(),
                membro.getId(),
                membro.getNome() + " desistiu de " + funcao.getNome() + ", " + quando(evento) + " · " + evento.getNome()
                        + " (" + ministerio + ", escala publicada). A vaga ficou vazia."));
        return "Você desistiu de " + onde + ". O gerente vê o aviso na escala";
    }

    /** A pessoa está na vaga, ou desistiu dela e ninguém entrou no lugar. */
    private static boolean daPessoa(Vaga vaga, Long usuarioId) {
        return usuarioId.equals(vaga.getUsuarioId()) || usuarioId.equals(vaga.getDesistenteId());
    }

    /** "12/10 · Dom · 18h00". */
    private static String quando(Evento evento) {
        return AjusteDaEscala.quando(evento.getInicio());
    }

    private static <T> T unico(List<T> itens) {
        return itens.isEmpty() ? null : itens.getFirst();
    }

    private static NaoEncontradoException naoEncontrada(Long vagaId, UsuarioAutenticado membro) {
        return new NaoEncontradoException("Vaga " + vagaId + " da pessoa " + membro.getId());
    }
}
