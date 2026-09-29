# syntax=docker/dockerfile:1

# O build roda na arquitetura de quem compila (sem emulação QEMU): o jar independe de CPU.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml package.json package-lock.json ./
COPY src/ src/
COPY docs/design/ docs/design/

# Testes, Spotless e JaCoCo já rodam no CI; aqui só empacota.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && cp target/escala-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extraido

# O runtime não tem RUN: a variante arm64 é montada só com COPY, sem emulação.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Camadas da mais estável para a mais volátil, para aproveitar o cache.
COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=America/Sao_Paulo

# Usuário sem privilégios que já existe na imagem base (Ubuntu); o código fica somente leitura (dono root).
USER 1000:1000
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
