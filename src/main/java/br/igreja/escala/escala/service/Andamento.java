package br.igreja.escala.escala.service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;

/**
 * A geração de um período em andamento, ou o resultado dela até a página avisar o gerente. Fica só em memória: o app
 * roda numa instância, e um reinício no meio só perde a geração (o rascunho anterior fica como estava).
 */
public final class Andamento {

    public enum Estado {
        GERANDO,
        CONCLUIDA,
        FALHOU
    }

    private final Long periodoId;
    private final Long ministerioId;
    private final YearMonth mes;
    private final Long autorId;
    private final Instant inicio;
    private final CompletableFuture<Andamento> fim = new CompletableFuture<>();

    private volatile Estado estado = Estado.GERANDO;
    private volatile int preenchidas;
    private volatile int vagas;
    private volatile String mensagem;
    private volatile Duration duracao;

    Andamento(Long periodoId, Long ministerioId, YearMonth mes, Long autorId, Instant inicio) {
        this.periodoId = periodoId;
        this.ministerioId = ministerioId;
        this.mes = mes;
        this.autorId = autorId;
        this.inicio = inicio;
    }

    /** A melhor escala até agora: quantas vagas já têm alguém. */
    void melhorAte(int preenchidas, int vagas) {
        this.preenchidas = preenchidas;
        this.vagas = vagas;
    }

    void concluir(int preenchidas, int vagas, Duration duracao, String mensagem) {
        melhorAte(preenchidas, vagas);
        this.duracao = duracao;
        this.mensagem = mensagem;
        this.estado = Estado.CONCLUIDA;
        fim.complete(this);
    }

    void falhar(String mensagem) {
        this.mensagem = mensagem;
        this.estado = Estado.FALHOU;
        fim.complete(this);
    }

    public boolean isGerando() {
        return estado == Estado.GERANDO;
    }

    public Long getPeriodoId() {
        return periodoId;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public YearMonth getMes() {
        return mes;
    }

    public Long getAutorId() {
        return autorId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Estado getEstado() {
        return estado;
    }

    public int getPreenchidas() {
        return preenchidas;
    }

    public int getVagas() {
        return vagas;
    }

    /** O que dizer ao gerente quando termina: o resumo ou o motivo da falha. */
    public String getMensagem() {
        return mensagem;
    }

    public Duration getDuracao() {
        return duracao;
    }

    /** Completa quando a geração termina, com sucesso ou não (os testes esperam por ela). */
    public CompletableFuture<Andamento> getFim() {
        return fim;
    }
}
