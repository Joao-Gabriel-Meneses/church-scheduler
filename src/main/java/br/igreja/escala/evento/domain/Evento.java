package br.igreja.escala.evento.domain;

import br.igreja.escala.compartilhado.Exigencias;
import br.igreja.escala.compartilhado.domain.HorarioEmMinutos;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Um evento do mês: gerado de um modelo (mantém o vínculo, e a data é a do modelo) ou avulso. Editar o evento não muda
 * o modelo. Cancelado continua no banco, para o histórico e para gerar o mês de novo sem recriá-lo.
 */
@Entity
@Table(name = "evento")
public class Evento {

    public static final int TAMANHO_NOME = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ministerio_id", nullable = false, updatable = false)
    private Long ministerioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "periodo_id", nullable = false)
    private Periodo periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id", updatable = false)
    private ModeloEvento modelo;

    @Column(nullable = false, length = TAMANHO_NOME)
    private String nome;

    @Column(name = "data_evento", nullable = false)
    private LocalDate data;

    @Convert(converter = HorarioEmMinutos.class)
    @Column(name = "horario_minutos", nullable = false)
    private LocalTime horario;

    @Column(nullable = false)
    private boolean cancelado;

    protected Evento() {}

    private Evento(Periodo periodo, ModeloEvento modelo, String nome, LocalDate data, LocalTime horario) {
        this.ministerioId = Exigencias.presente(periodo, "periodo").getMinisterioId();
        this.modelo = modelo;
        moverPara(data, periodo);
        alterar(nome, horario);
    }

    /** Evento do modelo numa data do dia da semana dele, com o nome e o horário padrão. */
    public static Evento doModelo(ModeloEvento modelo, Periodo periodo, LocalDate data) {
        Exigencias.presente(modelo, "modelo");
        if (!modelo.getMinisterioId().equals(periodo.getMinisterioId())) {
            throw new IllegalArgumentException("o modelo e o período precisam ser do mesmo ministério");
        }
        if (data.getDayOfWeek() != modelo.getDiaDaSemana()) {
            throw new IllegalArgumentException("a data " + data + " não é " + modelo.getDiaDaSemana());
        }
        return new Evento(periodo, modelo, modelo.getNome(), data, modelo.getHorario());
    }

    public static Evento avulso(Periodo periodo, String nome, LocalDate data, LocalTime horario) {
        return new Evento(periodo, null, nome, data, horario);
    }

    public void alterar(String nome, LocalTime horario) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.horario = Exigencias.presente(horario, "horario").withSecond(0).withNano(0);
    }

    /**
     * Muda a data de um evento avulso, no período do mês novo. O evento de um modelo fica na data do modelo: para outra
     * data, cancela-se e cria-se um avulso.
     */
    public void mudarData(LocalDate data, Periodo periodo) {
        if (!isAvulso()) {
            throw new IllegalStateException("evento de modelo não muda de data");
        }
        moverPara(data, periodo);
    }

    public void cancelar() {
        this.cancelado = true;
    }

    public void reativar() {
        this.cancelado = false;
    }

    public boolean isAvulso() {
        return modelo == null;
    }

    private void moverPara(LocalDate data, Periodo periodo) {
        Exigencias.presente(data, "data");
        Exigencias.presente(periodo, "periodo");
        if (!periodo.contem(data) || !periodo.getMinisterioId().equals(ministerioId)) {
            throw new IllegalArgumentException("o período precisa ser do mês da data e do ministério do evento");
        }
        this.data = data;
        this.periodo = periodo;
    }

    public Long getId() {
        return id;
    }

    public Long getMinisterioId() {
        return ministerioId;
    }

    public Periodo getPeriodo() {
        return periodo;
    }

    public ModeloEvento getModelo() {
        return modelo;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public boolean isCancelado() {
        return cancelado;
    }
}
