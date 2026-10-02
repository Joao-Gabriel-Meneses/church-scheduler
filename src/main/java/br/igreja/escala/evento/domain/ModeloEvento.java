package br.igreja.escala.evento.domain;

import br.igreja.escala.compartilhado.Exigencias;
import br.igreja.escala.compartilhado.domain.DuracaoEmMinutos;
import br.igreja.escala.compartilhado.domain.HorarioEmMinutos;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

    @Convert(converter = DuracaoEmMinutos.class)
    @Column(name = "duracao_minutos", nullable = false)
    private Duration duracao;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Ids das funções que os eventos do modelo precisam; vazio = todas as do ministério (o padrão). */
    @ElementCollection
    @CollectionTable(name = "modelo_evento_funcao", joinColumns = @JoinColumn(name = "modelo_id"))
    @Column(name = "funcao_id", nullable = false)
    private Set<Long> funcoesExigidas = new HashSet<>();

    protected ModeloEvento() {}

    public ModeloEvento(Long ministerioId, String nome, DayOfWeek diaDaSemana, LocalTime horario, Duration duracao) {
        this.ministerioId = Exigencias.presente(ministerioId, "ministerioId");
        alterar(nome, diaDaSemana, horario, duracao, true);
    }

    public void alterar(String nome, DayOfWeek diaDaSemana, LocalTime horario, Duration duracao, boolean ativo) {
        this.nome = Exigencias.texto(nome, "nome", TAMANHO_NOME);
        this.diaDaSemana = Exigencias.presente(diaDaSemana, "diaDaSemana");
        this.horario = Exigencias.presente(horario, "horario").withSecond(0).withNano(0);
        this.duracao = Duracoes.exigirValida(duracao);
        this.ativo = ativo;
    }

    /** Troca as funções dos próximos eventos; os já criados ficam como estão. Vazio volta ao padrão: todas. */
    public void exigirFuncoes(Collection<Long> funcoes) {
        Exigencias.presente(funcoes, "funcoes");
        funcoesExigidas.clear();
        funcoesExigidas.addAll(funcoes);
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

    public Duration getDuracao() {
        return duracao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Vazio quando os eventos do modelo precisam de todas as funções do ministério. */
    public Set<Long> getFuncoesExigidas() {
        return Collections.unmodifiableSet(funcoesExigidas);
    }
}
