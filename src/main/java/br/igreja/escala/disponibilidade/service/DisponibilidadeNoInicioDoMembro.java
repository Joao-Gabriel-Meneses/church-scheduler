package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.web.DisponibilidadeNoInicio;
import br.igreja.escala.compartilhado.web.ResumoDaDisponibilidade;
import br.igreja.escala.evento.service.EventoService;
import org.springframework.stereotype.Component;

/** O resumo do início: a mesma tela de disponibilidade do próximo mês, só com a contagem de cada ministério. */
@Component
class DisponibilidadeNoInicioDoMembro implements DisponibilidadeNoInicio {

    private final ConsultaDaDisponibilidade consulta;
    private final EventoService eventos;

    DisponibilidadeNoInicioDoMembro(ConsultaDaDisponibilidade consulta, EventoService eventos) {
        this.consulta = consulta;
        this.eventos = eventos;
    }

    @Override
    public ResumoDaDisponibilidade doMembro(Long usuarioId) {
        var mes = eventos.proximoMes();
        var tela = consulta.doMembro(usuarioId, mes);
        return new ResumoDaDisponibilidade(
                Datas.nomeDoMes(mes),
                "/disponibilidade?mes=" + mes,
                tela.grupos().stream()
                        .map(grupo -> new ResumoDaDisponibilidade.Ministerio(
                                grupo.ministerio(), grupo.contagem(), grupo.travado()))
                        .toList());
    }
}
