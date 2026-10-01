package br.igreja.escala.compartilhado.web;

import br.igreja.escala.compartilhado.RegraVioladaException;
import org.springframework.validation.BindingResult;

/** Ajudas para os controllers que recebem formulários. */
public final class Formularios {

    private Formularios() {}

    /**
     * Mostra a recusa de uma regra no formulário: no campo indicado ou, sem campo, como erro geral (o fragmento
     * {@code componentes/formulario :: errosGerais} vira AlertBanner).
     */
    public static void rejeitar(BindingResult erros, RegraVioladaException recusa) {
        if (recusa.campo() == null) {
            erros.reject("RegraViolada", recusa.getMessage());
        } else {
            erros.rejectValue(recusa.campo(), "RegraViolada", recusa.getMessage());
        }
    }
}
