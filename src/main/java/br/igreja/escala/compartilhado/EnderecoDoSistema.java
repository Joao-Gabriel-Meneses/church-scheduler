package br.igreja.escala.compartilhado;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Endereço público do sistema, para os links que saem dele (lembrete no WhatsApp; depois, os e-mails). Em produção é
 * {@code https://${DOMINIO}}; no dev, {@code http://localhost:8080}.
 *
 * @param urlBase {@code escala.url-base}, sem a barra final
 */
@Validated
@ConfigurationProperties("escala")
public record EnderecoDoSistema(
        @NotBlank @Pattern(regexp = "https?://[^/\\s]+.*", message = "precisa ser um endereço http(s) com domínio")
        String urlBase) {

    public EnderecoDoSistema {
        urlBase = urlBase == null ? null : urlBase.strip().replaceAll("/+$", "");
    }

    /** O link completo para um caminho do app ("/disponibilidade?mes=2026-11"). */
    public String link(String caminho) {
        return urlBase + (caminho.startsWith("/") ? caminho : "/" + caminho);
    }
}
