package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.evento.domain.Evento;

/**
 * Evento na lista do mês, com data e horário já escritos.
 *
 * @param dia "04"
 * @param diaDaSemana "Dom"
 * @param data "04/10 · Dom"
 * @param horario "18h00"
 */
public record EventoResumo(
        Long id,
        String nome,
        String dia,
        String diaDaSemana,
        String data,
        String horario,
        boolean avulso,
        boolean cancelado) {

    static EventoResumo de(Evento evento) {
        return new EventoResumo(
                evento.getId(),
                evento.getNome(),
                Datas.dia(evento.getData()),
                Datas.diaDaSemanaCurto(evento.getData().getDayOfWeek()),
                Datas.dataCurta(evento.getData()),
                Datas.horario(evento.getHorario()),
                evento.isAvulso(),
                evento.isCancelado());
    }

    /** "04/10 · Dom · 18h00", com " · Avulso" e " · Cancelado" quando for o caso. */
    public String descricao() {
        return data + " · " + horario + (avulso ? " · Avulso" : "") + (cancelado ? " · Cancelado" : "");
    }
}
