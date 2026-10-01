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
import org.springframework.format.annotation.DateTimeFormat;

/** Dados de um modelo de evento; também é o objeto do formulário. */
public record DadosDoModelo(
        @NotBlank(message = "Informe o nome do evento, como Culto de domingo.") @Size(max = ModeloEvento.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Escolha o dia da semana.") DayOfWeek diaDaSemana,

        @NotNull(message = "Informe o horário, como 18:00.") @DateTimeFormat(pattern = "HH:mm")
        LocalTime horario,

        @NotNull(message = "Informe a duração em minutos, como 120.") @Min(value = Duracoes.MINIMA_EM_MINUTOS, message = "A duração vai de {value} a 1440 minutos.") @Max(value = Duracoes.MAXIMA_EM_MINUTOS, message = "A duração vai de 15 a {value} minutos.") Integer duracaoMinutos,

        boolean ativo) {

    /** Formulário novo já com a duração de um culto. */
    public static DadosDoModelo novo() {
        return new DadosDoModelo(null, null, null, Duracoes.PADRAO_EM_MINUTOS, true);
    }

    public static DadosDoModelo de(ModeloEvento modelo) {
        return new DadosDoModelo(
                modelo.getNome(),
                modelo.getDiaDaSemana(),
                modelo.getHorario(),
                Math.toIntExact(modelo.getDuracao().toMinutes()),
                modelo.isAtivo());
    }

    public Duration duracao() {
        return Duration.ofMinutes(duracaoMinutos);
    }
}
