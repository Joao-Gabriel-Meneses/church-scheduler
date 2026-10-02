package br.igreja.escala.escala.web.dto;

import br.igreja.escala.escala.domain.MaxPorNivelParams;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** O formulário do máximo por nível no evento (MAX_POR_NIVEL_NO_EVENTO): ligada, o nível e quantas pessoas dele. */
public record DadosDoMaximoPorNivel(
        boolean ligada,
        Long nivelId,

        @NotNull(message = "Informe quantas pessoas do nível podem servir no mesmo evento.") @Min(value = MaxPorNivelParams.MINIMO, message = "O máximo vai de {value} a 20 pessoas por evento.") @Max(value = MaxPorNivelParams.MAXIMO, message = "O máximo vai de 1 a {value} pessoas por evento.") Integer maximo) {}
