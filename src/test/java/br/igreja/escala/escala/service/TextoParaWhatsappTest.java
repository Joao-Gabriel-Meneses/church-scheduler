package br.igreja.escala.escala.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.escala.service.EscaladosDoEvento.NaFuncao;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextoParaWhatsappTest {

    @Test
    void tituloEmNegritoADataEQuemEstaEmCadaFuncaoExigida() {
        var evento = new EscaladosDoEvento(
                501L,
                LocalDateTime.of(2026, 10, 11, 18, 0),
                "11/10 · Dom · 18h00",
                "Culto de domingo",
                List.of(
                        new NaFuncao("Projeção", true, List.of("Ana Souza")),
                        new NaFuncao("Transmissão", true, List.of(EscaladosDoEvento.A_DEFINIR)),
                        new NaFuncao("Som", true, List.of("Bruno Alves", "Carla Dias")),
                        new NaFuncao("Apoio", false, List.of())));

        assertThat(TextoParaWhatsapp.de("Mídia", evento)).isEqualTo("""
                        *Mídia — Culto de domingo*
                        11/10 · Dom · 18h00

                        Projeção: Ana Souza
                        Transmissão: a definir
                        Som: Bruno Alves, Carla Dias""");
    }
}
