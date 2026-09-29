package br.igreja.escala;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/** Sobe a aplicação com o perfil dev usando o Oracle do Testcontainers no lugar do compose.dev.yaml. */
public class TestEscalaApplication {

    public static void main(String[] args) {
        String[] argumentos = Stream.concat(Arrays.stream(args), Stream.of("--spring.docker.compose.enabled=false"))
                .toArray(String[]::new);
        SpringApplication.from(EscalaApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("dev")
                .run(argumentos);
    }
}
