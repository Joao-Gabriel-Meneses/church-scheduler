package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.evento.domain.Evento;

/**
 * Um evento no AvailabilityPicker, com a resposta da pessoa, já escrito como a tela mostra.
 *
 * @param dia "01"
 * @param diaDaSemana "Dom"
 * @param data "01/11 · Dom"
 * @param horario "18h00"
 * @param resposta nula se a pessoa ainda não respondeu
 * @param marcadoPor nome de quem marcou em nome da pessoa (o gerente); nulo se foi ela mesma
 * @param aviso o evento mudou depois da resposta: "O horário mudou (era 18h00). Toque de novo para confirmar."
 */
public record LinhaDeDisponibilidade(
        Long eventoId,
        String nome,
        String dia,
        String diaDaSemana,
        String data,
        String horario,
        Resposta resposta,
        String marcadoPor,
        String aviso) {

    static LinhaDeDisponibilidade de(Evento evento, Disponibilidade disponibilidade, String marcadoPor) {
        return new LinhaDeDisponibilidade(
                evento.getId(),
                evento.getNome(),
                Datas.dia(evento.getData()),
                Datas.diaDaSemanaCurto(evento.getData().getDayOfWeek()),
                Datas.dataCurta(evento.getData()),
                Datas.horario(evento.getHorario()),
                disponibilidade == null ? null : disponibilidade.getResposta(),
                disponibilidade != null && disponibilidade.marcadaPorOutro() ? marcadoPor : null,
                disponibilidade == null ? null : aviso(evento, disponibilidade));
    }

    private static String aviso(Evento evento, Disponibilidade disponibilidade) {
        if (!disponibilidade.eventoMudou(evento)) {
            return null;
        }
        String era = Datas.horario(disponibilidade.getHorarioNaResposta());
        if (!disponibilidade.getDataNaResposta().equals(evento.getData())) {
            return "A data mudou (era " + Datas.dataCurta(disponibilidade.getDataNaResposta()) + ", " + era
                    + "). Toque de novo para confirmar.";
        }
        return "O horário mudou (era " + era + "). Toque de novo para confirmar.";
    }

    public boolean respondida() {
        return resposta != null;
    }

    public boolean pode() {
        return resposta == Resposta.PODE;
    }

    public boolean naoPode() {
        return resposta == Resposta.NAO_PODE;
    }

    /** "01/11 · Dom · Culto de domingo, 18h00", para o aria-label dos botões. */
    public String descricao() {
        return data + " · " + nome + ", " + horario;
    }
}
