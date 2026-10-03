package br.igreja.escala.escala.web.dto;

import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** O formulário do limite do mês (LIMITE_POR_PERIODO). */
public record DadosDoLimite(
        @NotNull(message = "Informe em quantos eventos do mês cada pessoa pode servir.") @Min(value = LimitePorPeriodoParams.MINIMO, message = "O limite vai de {value} a 31 eventos no mês.") @Max(value = LimitePorPeriodoParams.MAXIMO, message = "O limite vai de 1 a {value} eventos no mês.") Integer maximo) {}
