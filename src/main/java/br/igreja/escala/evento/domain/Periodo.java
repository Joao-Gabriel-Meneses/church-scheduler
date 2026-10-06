package br.igreja.escala.evento.domain;

import br.igreja.escala.compartilhado.Exigencias;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;

/** Um mês de um ministério. Nasce quando o primeiro evento do mês é criado. */
@Entity
@Table(name = "periodo")
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ministerio_id", nullable = false, updatable = false)
    private Long ministerioId;

    @Column(nullable = false, updatable = false)
    private int ano;

    @Column(nullable = false, updatable = false)
    private int mes;

    @Column(name = "disponibilidade_travada", nullable = false)
    private boolean disponibilidadeTravada;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_escala", nullable = false, length = 10)
    private StatusDaEscala statusDaEscala = StatusDaEscala.RASCUNHO;

    protected Periodo() {}

    public Periodo(Long ministerioId, YearMonth mes) {
        this.ministerioId = Exigencias.presente(ministerioId, "ministerioId");
        Exigencias.presente(mes, "mes");
        this.ano = mes.getYear();
        this.mes = mes.getMonthValue();
    }

    public YearMonth getMes() {
        return YearMonth.of(ano, mes);
    }

    public boolean contem(LocalDate data) {
        return YearMonth.from(data).equals(getMes());
    }

    /**
     * Fecha a disponibilidade do mês: o membro não muda mais as respostas (o gerente ainda marca em nome dele).
     *
     * @return se travou agora; falso se já estava travada
     */
    public boolean travarDisponibilidade() {
        if (disponibilidadeTravada) {
            return false;
        }
        disponibilidadeTravada = true;
        return true;
    }

    /**
     * @return se destravou agora; falso se já estava aberta
     */
    public boolean destravarDisponibilidade() {
        if (!disponibilidadeTravada) {
            return false;
        }
        disponibilidadeTravada = false;
        return true;
    }

    /**
     * O rascunho vira a escala que os membros veem.
     *
     * @return se publicou agora; falso se já estava publicada
     */
    public boolean publicarEscala() {
        if (isEscalaPublicada()) {
            return false;
        }
        statusDaEscala = StatusDaEscala.PUBLICADA;
        return true;
    }

    /**
     * A escala volta a ser rascunho e some da visão dos membros (a geração automática volta a valer).
     *
     * @return se reabriu agora; falso se já era rascunho
     */
    public boolean reabrirEscala() {
        if (!isEscalaPublicada()) {
            return false;
        }
        statusDaEscala = StatusDaEscala.RASCUNHO;
        return true;
    }

    public boolean isEscalaPublicada() {
        return statusDaEscala == StatusDaEscala.PUBLICADA;
    }

    public Long getId() {
        return id;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public boolean isDisponibilidadeTravada() {
        return disponibilidadeTravada;
    }

    public StatusDaEscala getStatusDaEscala() {
        return statusDaEscala;
    }
}
