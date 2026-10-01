package br.igreja.escala.disponibilidade.domain;

import br.igreja.escala.compartilhado.Exigencias;
import br.igreja.escala.compartilhado.domain.HorarioEmMinutos;
import br.igreja.escala.evento.domain.Evento;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A resposta de uma pessoa a um evento, uma por usuário e evento. Usuário e evento são de outros módulos, então ficam
 * só pelo id.
 *
 * <p>Guarda também a data e o horário do evento no momento da resposta: se o evento mudar depois, a resposta continua,
 * e a tela avisa a pessoa para conferir ({@link #eventoMudou}).
 */
@Entity
@Table(name = "disponibilidade")
public class Disponibilidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Resposta resposta;

    @Column(name = "data_na_resposta", nullable = false)
    private LocalDate dataNaResposta;

    @Convert(converter = HorarioEmMinutos.class)
    @Column(name = "horario_na_resposta_minutos", nullable = false)
    private LocalTime horarioNaResposta;

    @Column(name = "marcado_por_id", nullable = false)
    private Long marcadoPorId;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Disponibilidade() {}

    /**
     * Primeira resposta da pessoa ao evento.
     *
     * @param marcadoPorId a própria pessoa ou o gerente que marcou em nome dela
     */
    public Disponibilidade(Long usuarioId, Evento evento, Resposta resposta, Long marcadoPorId) {
        this.usuarioId = Exigencias.presente(usuarioId, "usuarioId");
        this.eventoId =
                Exigencias.presente(Exigencias.presente(evento, "evento").getId(), "evento.id");
        guardar(resposta, evento, marcadoPorId);
    }

    /**
     * Marca de novo. A mesma resposta para o evento como ele está agora não muda nada, nem quem marcou: é o toque
     * duplo, ou o gerente confirmando o que a pessoa já respondeu.
     *
     * @return se mudou a resposta ou o retrato do evento (e então quem marcou)
     */
    public boolean marcar(Resposta resposta, Evento evento, Long marcadoPorId) {
        Exigencias.presente(resposta, "resposta");
        Exigencias.presente(marcadoPorId, "marcadoPorId");
        if (!eventoId.equals(Exigencias.presente(evento, "evento").getId())) {
            throw new IllegalArgumentException("a resposta é do evento " + eventoId + ", não do " + evento.getId());
        }
        if (resposta == this.resposta && !eventoMudou(evento)) {
            return false;
        }
        guardar(resposta, evento, marcadoPorId);
        return true;
    }

    /** O evento mudou de data ou de horário depois da resposta. */
    public boolean eventoMudou(Evento evento) {
        return !evento.getData().equals(dataNaResposta) || !evento.getHorario().equals(horarioNaResposta);
    }

    /** Quem marcou foi o gerente, não a própria pessoa. */
    public boolean marcadaPorOutro() {
        return !marcadoPorId.equals(usuarioId);
    }

    private void guardar(Resposta resposta, Evento evento, Long marcadoPorId) {
        this.resposta = Exigencias.presente(resposta, "resposta");
        this.marcadoPorId = Exigencias.presente(marcadoPorId, "marcadoPorId");
        this.dataNaResposta = evento.getData();
        this.horarioNaResposta = evento.getHorario();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Resposta getResposta() {
        return resposta;
    }

    public LocalDate getDataNaResposta() {
        return dataNaResposta;
    }

    public LocalTime getHorarioNaResposta() {
        return horarioNaResposta;
    }

    public Long getMarcadoPorId() {
        return marcadoPorId;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
