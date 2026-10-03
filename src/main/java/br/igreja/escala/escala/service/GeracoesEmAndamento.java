package br.igreja.escala.escala.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Uma geração por período de cada vez: o segundo clique encontra a que está rodando e não dispara outra. */
@Component
class GeracoesEmAndamento {

    private final ConcurrentHashMap<Long, Andamento> porPeriodo = new ConcurrentHashMap<>();

    /** Registra a geração nova, ou devolve a que já está gerando o período (atômico). */
    Andamento registrar(Andamento nova) {
        return porPeriodo.compute(
                nova.getPeriodoId(), (periodo, atual) -> atual != null && atual.isGerando() ? atual : nova);
    }

    Optional<Andamento> doPeriodo(Long periodoId) {
        return Optional.ofNullable(porPeriodo.get(periodoId));
    }

    /** Tira o resultado de uma geração que já terminou, para avisar o gerente uma vez só. */
    Optional<Andamento> retirarTerminada(Long periodoId) {
        var terminada = new Andamento[1];
        porPeriodo.computeIfPresent(periodoId, (periodo, atual) -> {
            if (atual.isGerando()) {
                return atual;
            }
            terminada[0] = atual;
            return null;
        });
        return Optional.ofNullable(terminada[0]);
    }

    void remover(Andamento andamento) {
        porPeriodo.remove(andamento.getPeriodoId(), andamento);
    }
}
