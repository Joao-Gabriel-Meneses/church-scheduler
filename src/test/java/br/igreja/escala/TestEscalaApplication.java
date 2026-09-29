package br.igreja.escala;

import org.springframework.boot.SpringApplication;

/** Sobe a aplicação com o Oracle do Testcontainers, sem precisar do compose.dev.yaml. */
public class TestEscalaApplication {

    public static void main(String[] args) {
        SpringApplication.from(EscalaApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
