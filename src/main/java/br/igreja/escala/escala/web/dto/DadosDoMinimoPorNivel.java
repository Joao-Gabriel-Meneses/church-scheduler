package br.igreja.escala.escala.web.dto;

import br.igreja.escala.escala.domain.MinPorNivelParams;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** O formulário do mínimo por nível no evento (MIN_POR_NIVEL_NO_EVENTO): ligada, o nível e quantas pessoas dele. */
public record DadosDoMinimoPorNivel(
        boolean ligada,
        Long nivelId,

        @NotNull(message = "Informe quantas pessoas do nível cada evento precisa ter.") @Min(value = MinPorNivelParams.MINIMO, message = "O mínimo vai de {value} a 20 pessoas por evento.") @Max(value = MinPorNivelParams.MAXIMO, message = "O mínimo vai de 1 a {value} pessoas por evento.") Integer minimo) {}
