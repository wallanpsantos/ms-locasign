# syntax=docker/dockerfile:1

# ---- Estágio de build: JDK 25 e o wrapper do Gradle ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Arquivos de build primeiro, para aproveitar o cache de camadas enquanto o código muda.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# Extrai o jar em camadas (dependências mudam menos que o código da aplicação).
RUN java -Djarmode=tools -jar build/libs/app.jar extract --layers --launcher --destination /workspace/layers

# ---- Estágio final: apenas JRE 25, usuário não root ----
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system locasign \
    && useradd --system --gid locasign --home-dir /app locasign

WORKDIR /app
COPY --from=build --chown=locasign:locasign /workspace/layers/dependencies/ ./
COPY --from=build --chown=locasign:locasign /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=locasign:locasign /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=locasign:locasign /workspace/layers/application/ ./

RUN mkdir -p /app/data && chown locasign:locasign /app/data
USER locasign

EXPOSE 8080

# Memória por porcentagem do container; fuso UTC (instantes sempre em UTC).
# Virtual threads vêm de `spring.threads.virtual.enabled=true` no application.yaml.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
