package br.igreja.escala.disponibilidade.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.EnderecoDoSistema;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** O lembrete que o gerente cola no grupo do WhatsApp, com o link da tela de disponibilidade. Não envia nada. */
@Service
public class LembreteDaDisponibilidade {

    private final EnderecoDoSistema endereco;

    LembreteDaDisponibilidade(EnderecoDoSistema endereco) {
        this.endereco = endereco;
    }

    /**
     * Título em negrito do WhatsApp, o convite com o link do mês e, se alguém ainda não respondeu tudo, os nomes:
     *
     * <pre>
     * *Mídia — Novembro*
     * A disponibilidade de novembro está aberta. Marque em quais eventos você pode servir: https://…/disponibilidade?mes=2026-11
     *
     * Ainda faltam: Bruno Lima, Carla Dias.
     * </pre>
     */
    public String texto(String ministerio, PainelDaDisponibilidade painel) {
        var texto = new StringBuilder()
                .append('*')
                .append(ministerio)
                .append(" — ")
                .append(Datas.nomeDoMes(painel.mes()))
                .append("*\n")
                .append("A disponibilidade de ")
                .append(painel.nomeDoMes())
                .append(" está aberta. Marque em quais eventos você pode servir: ")
                .append(endereco.link("/disponibilidade?mes=" + painel.mes()));
        var faltam = painel.faltamResponder();
        if (!faltam.isEmpty()) {
            texto.append("\n\nAinda faltam: ")
                    .append(faltam.stream().map(linha -> linha.pessoa().nome()).collect(Collectors.joining(", ")))
                    .append('.');
        }
        return texto.toString();
    }
}
