package br.igreja.escala.evento.service;

import br.igreja.escala.evento.domain.Evento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Dados de um evento avulso, ou o que muda num evento; também é o objeto do formulário. */
public record DadosDoEvento(
        @NotBlank(message = "Informe o nome do evento.") @Size(max = Evento.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Informe a data.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate data,

        @NotNull(message = "Informe o horário, como 19:30.") @DateTimeFormat(pattern = "HH:mm")
        LocalTime horario) {

    public static DadosDoEvento vazio() {
        return new DadosDoEvento(null, null, null);
    }

    public static DadosDoEvento de(Evento evento) {
        return new DadosDoEvento(evento.getNome(), evento.getData(), evento.getHorario());
    }
}
