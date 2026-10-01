package br.igreja.escala.evento.service;

import br.igreja.escala.evento.domain.ModeloEvento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/** Dados de um modelo de evento; também é o objeto do formulário. */
public record DadosDoModelo(
        @NotBlank(message = "Informe o nome do evento, como Culto de domingo.") @Size(max = ModeloEvento.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Escolha o dia da semana.") DayOfWeek diaDaSemana,

        @NotNull(message = "Informe o horário, como 18:00.") @DateTimeFormat(pattern = "HH:mm")
        LocalTime horario,

        boolean ativo) {

    public static DadosDoModelo novo() {
        return new DadosDoModelo(null, null, null, true);
    }

    public static DadosDoModelo de(ModeloEvento modelo) {
        return new DadosDoModelo(modelo.getNome(), modelo.getDiaDaSemana(), modelo.getHorario(), modelo.isAtivo());
    }
}
