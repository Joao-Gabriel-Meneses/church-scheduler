package br.igreja.escala.identidade.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Admin criado na primeira subida. Em produção: ESCALA_ADMIN_NOME, ESCALA_ADMIN_EMAIL, ESCALA_ADMIN_SENHA. */
@ConfigurationProperties("escala.admin")
public record AdminInicialProperties(String nome, String email, String senha) {

    public boolean configurado() {
        return email != null && !email.isBlank();
    }
}
