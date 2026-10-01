package br.igreja.escala.evento.domain;

import br.igreja.escala.compartilhado.Exigencias;
import br.igreja.escala.compartilhado.domain.HorarioEmMinutos;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Evento que se repete toda semana ("Culto de domingo", domingo, 18h00). Os eventos do mês nascem dele; mudar o modelo
 * depois não muda eventos já criados. O ministério é de outro módulo, então fica só o id.
 */
@Entity
@Table(name = "modelo_evento")
public class ModeloEvento {

    public static final int TAMANHO_NOME = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ministerio_id", nullable = false, updatable = false)
    private Long ministerioId;

    @Column(nullable = false, length = TAMANHO_NOME)
    private String nome;

    @Convert(converter = DiaDaSemanaConverter.class)
    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek diaDaSemana;

    @Convert(converter = HorarioEmMinutos.class)
    @Column(name = "horario_minutos", nullable = false)
    private LocalTime horario;

    @Column(nullable = false)
    private boolean ativo = true;

    protected ModeloEvento() {}

    public ModeloEvento(Long ministerioId, String nome, DayOfWeek diaDaSemana, LocalTime horario) {
        this.ministerioId = Exigencias.presente(ministerioId, "ministerioId");
        alterar(nome, diaDaSemana, horario, true);
    }

    public void alterar(String nome, DayOfWeek diaDaSemana, LocalTime horario, boolean ativo) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.diaDaSemana = Exigencias.presente(diaDaSemana, "diaDaSemana");
        this.horario = Exigencias.presente(horario, "horario").withSecond(0).withNano(0);
        this.ativo = ativo;
    }

    public Long getId() {
        return id;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public String getNome() {
        return nome;
    }

    public DayOfWeek getDiaDaSemana() {
        return diaDaSemana;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
