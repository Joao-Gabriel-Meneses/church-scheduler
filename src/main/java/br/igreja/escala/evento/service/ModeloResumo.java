package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.evento.domain.ModeloEvento;

/** Modelo de evento na lista do gerente, com dia e horário já escritos ("Domingo", "18h00 às 20h00"). */
public record ModeloResumo(Long id, String nome, String diaDaSemana, String horario, boolean ativo) {

    static ModeloResumo de(ModeloEvento modelo) {
        return new ModeloResumo(
                modelo.getId(),
                modelo.getNome(),
                Datas.diaDaSemana(modelo.getDiaDaSemana()),
                Datas.faixaDeHorario(modelo.getHorario(), modelo.getDuracao()),
                modelo.isAtivo());
    }

    /** "Domingo · 18h00 às 20h00", ou com " · Inativo". */
    public String descricao() {
        return diaDaSemana + " · " + horario + (ativo ? "" : " · Inativo");
    }
}
