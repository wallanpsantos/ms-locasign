# Build, Governança de Dependências e Integração Contínua (CI)

> **Origem:** Este documento detalha a seção 7 original do `AGENTS.md`. Em caso de conflito, as regras do núcleo do [
`AGENTS.md`](../../AGENTS.md) prevalecem.

## 1. Governança de dependências

- **Origem das versões:** `gradle/libs.versions.toml` fixa as versões de catálogo:
    - Kotlin: `2.4.21` (plugins `kotlin-jvm` e `kotlin-spring`); sobrepõe a linha Kotlin 2.3.x do BOM;
    - Spring Boot: `4.1.1` (plugin `spring-boot`);
    - `io.spring.dependency-management`: `1.1.7`;
    - springdoc: `3.1.1` (`springdoc-openapi-starter-webmvc-ui`);
    - WireMock: `3.13.2` (`wiremock-standalone`, incluído via ADR-014);
    - ArchUnit: `1.5.1` (`archunit-junit5`, incluído via ADR-014).

  Todo o restante das dependências (starters Spring Boot, Jackson 3, Flyway, driver PostgreSQL, `kafka-clients`, JUnit,
  Mockito, AssertJ, Awaitility, Testcontainers) vem gerenciado pelo BOM `spring-boot-dependencies` e é declarado em
  `build.gradle.kts` **sem versão**.
- **Exige ADR quando o agente faz a mudança:** adicionar, remover ou trocar a versão de uma dependência, inclusive
  bibliotecas de teste fora do build (como MockK, Konsist e `mockito-kotlin`). Também exigem ADR alterar a toolchain
  Java (fixada em Java 25), o wrapper do Gradle, imagens Docker de base ou infraestrutura (`eclipse-temurin`,
  `postgres:18.6`, `apache/kafka:4.3.1`) e qualquer sobrescrita de versão gerenciada pelo BOM.
- **Como adicionar dependências, após aprovação de ADR:**
    1. Declare a coordenada sem versão e valide com:
       `./gradlew dependencyInsight --dependency <artefato> --configuration <runtimeClasspath|testRuntimeClasspath>`. Se
       o artefato resolver a partir do BOM, mantenha-o sem versão explícita.
    2. Se não resolver via BOM, declare `[versions]` e `[libraries]` no catálogo `gradle/libs.versions.toml` e
       referencie
       como `libs.<alias>` no `build.gradle.kts`.
    3. Nunca fixe versão pontual no `build.gradle.kts` para artefatos já gerenciados pelo BOM do Spring Boot (sem
       versões
       forçadas via `resolutionStrategy` ou propriedades `extra`).
    4. O Testcontainers adotado está na linha 2.x: artefatos nomeados no padrão
       `org.testcontainers:testcontainers-<módulo>`.
- **Dependabot:** atualizações automáticas de rotina chegam semanalmente via PRs do Dependabot, com revisão humana.
  O agente não realiza bumps de versão por iniciativa própria.

## 2. Contratos de build

- **Artefato único `build/libs/app.jar`:** a task `bootJar` gera diretamente o arquivo renomeado para `app.jar` e a task
  legada `jar` está desabilitada. Esse caminho e nome são consumidos pelo `Dockerfile`, pelo workflow
  `.github/workflows/gradle.yml`
  (upload com `if-no-files-found: error`) e por `.github/workflows/release.yml`. Nunca altere o nome do jar sem
  sincronizar
  esses três consumidores.
- **Dockerfile:** o estágio de compilação copia estritamente `gradlew`, `settings.gradle.kts`, `build.gradle.kts`,
  `gradle/` e `src/`, executando `bootJar -x test`. Qualquer arquivo novo na raiz consumido no build deve ser incluído
  no `COPY`.
- **Compilação e tipagem:**
    - O Spring Framework 7 adota anotações de nulidade JSpecify, interpretadas pelo Kotlin como tipos anuláveis (`T?`).
    - A flag do compilador `-Xjsr305=strict` garante compatibilidade estrita com bibliotecas que utilizam anotações
      JSR-305.
    - O plugin `kotlin-spring` abre automaticamente classes e métodos gerenciados pelo Spring (como `@Configuration`,
      `@Component`, `@Repository`).
- **Gradle Wrapper:** Gradle 9.8.1. A integridade do executável `gradle-wrapper.jar` é validada pela action
  `gradle/actions/wrapper-validation`
  na CI. Atualizações de wrapper exigem ADR e execução do comando `./gradlew wrapper --gradle-version <versao>`.
  O arquivo `.gitattributes` assegura que `gradlew` use quebras LF e `gradlew.bat` use CRLF.
- **Estrutura modular em projeto único:** `settings.gradle.kts` define apenas `rootProject.name = "locasign"`. A
  fronteira
  entre os módulos é realizada em nível de pacote dentro do mesmo módulo Gradle.
- **Informações de build:** o bloco `springBoot { buildInfo() }` injeta metadados de versão e horário em
  `/actuator/info`.

## 3. Integração Contínua (CI - GitHub Actions)

| Arquivo                                               | Gatilho                                        | O que faz                                                                                                                                                                                   |
|-------------------------------------------------------|------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.github/workflows/gradle.yml`                        | push e PR para `main`/`develop`, tags `v*.*.*` | Executa sob JDK 25: `./gradlew build --stacktrace` (`clean build` em `main` e tags); gera e publica `app.jar`; anexa relatórios de teste em falha; submete o grafo de dependências em push. |
| `.github/workflows/release.yml`                       | tags `v*.*.*`                                  | Executa `./gradlew clean build` e cria GitHub Release anexando o binário `app.jar`.                                                                                                         |
| `.github/workflows/dependency-review.yml`             | PR para `main`/`develop`                       | Bloqueia dependências com vulnerabilidade `high` ou superior detectadas no grafo de dependências do PR.                                                                                     |
| `.github/dependabot.yml` (configuração, não workflow) | semanal, às segundas                           | Atualiza dependências declaradas no catálogo `libs.versions.toml`, dependências de GitHub Actions e a imagem base do `Dockerfile`.                                                          |

- O comando `./gradlew build` executado localmente com Docker reproduz integralmente as validações do job principal de
  CI.
- Ao modificar workflows, preserve `permissions: contents: read` no escopo global, elevando privilégios estritamente nos
  jobs que necessitarem. Actions de terceiros devem ser fixadas por commit SHA acompanhado do comentário de versão
  (`# vX.Y.Z`).
