# church-scheduler — Escala Ministerial

Sistema web que gera automaticamente as escalas dos ministérios de uma igreja: cada membro marca sua disponibilidade, o sistema monta a escala respeitando as regras do ministério e o líder revisa e publica.

Stack: Java 21, Spring Boot 4.1, Thymeleaf + htmx + Tailwind, Oracle Autonomous Database 19c (testes no Oracle Free 23ai com dialeto 19), Flyway e Timefold. Os detalhes de arquitetura e as regras de negócio estão no [CLAUDE.md](CLAUDE.md).

## Pré-requisitos

- JDK 21
- Docker (Oracle Free local e Testcontainers)
- [pre-commit](https://pre-commit.com) (`pip install pre-commit`)

## Primeiros passos

```bash
pre-commit install          # ativa os hooks: formatação, segredos e arquivos grandes
./mvnw spring-boot:run      # perfil dev; sobe o Oracle Free via compose.dev.yaml
```

No perfil dev, o app cria dados de exemplo na primeira subida (Mídia e Louvor, com membros, funções e eventos). Entre como admin (`admin@escala.local` / `admin1234`) ou como gerente da Mídia (`paula.ribeiro@escala.local` / `membro1234`). A Carla Dias (`carla.dias@escala.local` / `provisoria-dev`) mostra o primeiro acesso com senha provisória.

## Testes e qualidade

```bash
./mvnw test                 # testes unitários
./mvnw verify               # + integração (Testcontainers), Spotless e cobertura (JaCoCo)
./mvnw spotless:apply       # formata o código Java e o pom.xml
pre-commit run --all-files  # roda todos os hooks
```

## Deploy

Produção roda em `escala.ibrp.com.br`: uma VM Oracle Cloud Always Free (ARM64) com Docker Compose (cloudflared + app), atrás de um Cloudflare Tunnel, e Autonomous Database 19c. O passo a passo, incluindo o backup diário, está em [docs/deploy.md](docs/deploy.md).
