package br.igreja.escala.evento.service;

import br.igreja.escala.evento.domain.Duracoes;
import br.igreja.escala.evento.domain.Evento;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Dados de um evento avulso, ou o que muda num evento; também é o objeto do formulário. */
public record DadosDoEvento(
        @NotBlank(message = "Informe o nome do evento.") @Size(max = Evento.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Informe a data.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate data,

        @NotNull(message = "Informe o horário, como 19:30.") @DateTimeFormat(pattern = "HH:mm")
        LocalTime horario,

        @NotNull(message = "Informe a duração em minutos, como 120.") @Min(value = Duracoes.MINIMA_EM_MINUTOS, message = "A duração vai de {value} a 1440 minutos.") @Max(value = Duracoes.MAXIMA_EM_MINUTOS, message = "A duração vai de 15 a {value} minutos.") Integer duracaoMinutos) {

    /** Formulário novo já com a duração de um culto. */
    public static DadosDoEvento vazio() {
        return new DadosDoEvento(null, null, null, Duracoes.PADRAO_EM_MINUTOS);
    }

    public static DadosDoEvento de(Evento evento) {
        return new DadosDoEvento(
                evento.getNome(),
                evento.getData(),
                evento.getHorario(),
                Math.toIntExact(evento.getDuracao().toMinutes()));
    }

    public Duration duracao() {
        return Duration.ofMinutes(duracaoMinutos);
    }
}
