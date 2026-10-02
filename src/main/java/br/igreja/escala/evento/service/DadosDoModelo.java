package br.igreja.escala.evento.service;

import br.igreja.escala.evento.domain.Duracoes;
import br.igreja.escala.evento.domain.ModeloEvento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Dados de um modelo de evento; também é o objeto do formulário. {@code funcoes} são os ids das funções que os eventos
 * do modelo precisam: nulo não muda nada (e, num modelo novo, fica com todas).
 */
public record DadosDoModelo(
        @NotBlank(message = "Informe o nome do evento, como Culto de domingo.") @Size(max = ModeloEvento.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Escolha o dia da semana.") DayOfWeek diaDaSemana,

        @NotNull(message = "Informe o horário, como 18:00.") @DateTimeFormat(pattern = "HH:mm")
        LocalTime horario,

        @NotNull(message = "Informe a duração em minutos, como 120.") @Min(value = Duracoes.MINIMA_EM_MINUTOS, message = "A duração vai de {value} a 1440 minutos.") @Max(value = Duracoes.MAXIMA_EM_MINUTOS, message = "A duração vai de 15 a {value} minutos.") Integer duracaoMinutos,

        boolean ativo,

        List<Long> funcoes) {

    /** Sem as funções: não muda as do modelo. */
    public DadosDoModelo(String nome, DayOfWeek diaDaSemana, LocalTime horario, Integer duracaoMinutos, boolean ativo) {
        this(nome, diaDaSemana, horario, duracaoMinutos, ativo, null);
    }

    /** Formulário novo já com a duração de um culto e todas as funções do ministério marcadas. */
    public static DadosDoModelo novo(List<Long> todasAsFuncoes) {
        return new DadosDoModelo(null, null, null, Duracoes.PADRAO_EM_MINUTOS, true, todasAsFuncoes);
    }

    /** O modelo no formulário; sem escolha de funções, todas aparecem marcadas. */
    public static DadosDoModelo de(ModeloEvento modelo, List<Long> todasAsFuncoes) {
        return new DadosDoModelo(
                modelo.getNome(),
                modelo.getDiaDaSemana(),
                modelo.getHorario(),
                Math.toIntExact(modelo.getDuracao().toMinutes()),
                modelo.isAtivo(),
                modelo.getFuncoesExigidas().isEmpty()
                        ? todasAsFuncoes
                        : modelo.getFuncoesExigidas().stream().sorted().toList());
    }

    public Duration duracao() {
        return Duration.ofMinutes(duracaoMinutos);
    }
}
