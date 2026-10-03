package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prepara o problema (vagas que faltam e que saem) e grava a escala que o solver achou, como rascunho. */
@Component
class GravacaoDaEscala {

    private final LeituraDoPeriodo leitura;
    private final VagaRepository vagas;
    private final PeriodoService periodos;
    private final MinisterioService ministerios;
    private final AuditoriaService auditoria;

    GravacaoDaEscala(
            LeituraDoPeriodo leitura,
            VagaRepository vagas,
            PeriodoService periodos,
            MinisterioService ministerios,
            AuditoriaService auditoria) {
        this.leitura = leitura;
        this.vagas = vagas;
        this.periodos = periodos;
        this.ministerios = ministerios;
        this.auditoria = auditoria;
    }

    /**
     * Cria as vagas que faltam nos eventos por vir, apaga as que saíram da escala e monta o problema com as vagas soltas
     * vazias. Roda na fila das gerações, quando chega a vez desta, então lê o que as anteriores já gravaram.
     */
    @Transactional
    public EscalaDoPeriodo preparar(Long ministerioId, YearMonth mes) {
        var dados = leitura.ler(ministerioId, mes);
        var reconciliacao = MontagemDaEscala.reconciliar(dados);
        vagas.deleteAll(reconciliacao.apagar());
        var ficaram = new ArrayList<>(dados.vagas());
        ficaram.removeAll(reconciliacao.apagar());
        ficaram.addAll(vagas.saveAll(reconciliacao.novas()));
        return MontagemDaEscala.montar(dados.comVagas(ficaram), true);
    }

    /**
     * Grava a pessoa de cada vaga solta e registra a geração. A trava é relida com o período bloqueado: se a
     * disponibilidade foi destravada durante a geração, nada muda.
     *
     * @return quantas vagas ficaram preenchidas
     * @throws RegraVioladaException se a disponibilidade foi destravada no meio
     */
    @Transactional
    public int gravar(EscalaDoPeriodo solucao, Andamento andamento, Duration duracao) {
        var periodo = periodos.bloquearParaAlterar(solucao.getPeriodoId());
        var nomeDoMes = Datas.nomeDoMes(andamento.getMes()).toLowerCase(Locale.ROOT);
        if (!periodo.isDisponibilidadeTravada()) {
            throw RegraVioladaException.geral("A disponibilidade de " + nomeDoMes
                    + " foi destravada durante a geração, e a escala não mudou. Trave de novo e gere a escala.");
        }
        Map<Long, Vaga> gravadas = vagas
                .findAllById(solucao.getVagas().stream()
                        .filter(vaga -> !vaga.isPresa())
                        .map(VagaPlanejada::getId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Vaga::getId, Function.identity()));
        for (VagaPlanejada planejada : solucao.getVagas()) {
            var vaga = gravadas.get(planejada.getId());
            if (vaga != null && !vaga.isPresa()) {
                vaga.escalar(
                        planejada.getPessoa() == null
                                ? null
                                : planejada.getPessoa().id());
            }
        }
        int preenchidas = (int) solucao.getVagas().stream()
                .filter(vaga -> vaga.getPessoa() != null)
                .count();
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.GERAR_ESCALA,
                andamento.getAutorId(),
                andamento.getMinisterioId(),
                null,
                "Escala de " + Datas.mesPorExtenso(andamento.getMes()) + " gerada ("
                        + ministerios.buscar(andamento.getMinisterioId()).getNome() + "): " + preenchidas + " de "
                        + solucao.getVagas().size() + " vagas preenchidas em " + Math.max(1, duracao.toSeconds())
                        + " s. Pontuação " + solucao.getPontuacao() + "."));
        return preenchidas;
    }
}
