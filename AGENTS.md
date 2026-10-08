# LocaSign: Diretrizes Universais para Agentes de IA

Serviço backend que automatiza o ciclo de vida de contratos de locação residencial: gera o contrato, envia para assinatura eletrônica na PandaDoc, acompanha todo o ciclo por webhooks e dispara as ações pós-assinatura.

> **Propósito do repositório:** Projeto de estudo focado em engenharia de software pragmática, Kotlin 2.4+, Spring Boot 4.1+, PostgreSQL 18+, Apache Kafka 4.3+ e Docker.  
> **Público deste arquivo:** Agentes de inteligência artificial (assistentes autônomos e ferramentas de pair programming, ex.: Antigravity, Claude Code, Cursor, GitHub Copilot, Codex, Windsurf, Aider) e desenvolvedores atuando no repositório.

---

## 1. Leitura Obrigatória Antes de Qualquer Tarefa

Antes de iniciar qualquer análise, planejamento ou implementação no projeto, consulte os seguintes documentos na ordem:

1. **`docs/plano-de-negocio.md`**: O que é o produto e o porquê. As regras de negócio **R1 a R10 prevalecem sobre qualquer outra decisão**.
2. **`docs/arquitetura-tecnica.md`**: O como (desenho arquitetural, contratos, glossário de nomes, fluxo de mensageria e decisões técnicas).
3. **`docs/adr/`**: Decisões de arquitetura registradas (ADRs). Qualquer decisão tomada após o guia inicial ou desvio acordado está formalizada aqui.

---

## 2. Comandos Operacionais

Utilize os comandos padrão do projeto via terminal:

```bash
docker compose up -d                                          # Sobe a infraestrutura (PostgreSQL + Kafka)
./gradlew bootRun --args='--spring.profiles.active=local'     # Executa a aplicação localmente via IDE/terminal
./gradlew build                                               # Compila o projeto e executa a suíte de testes
./gradlew test                                                # Executa testes unitários e de integração
docker compose --profile app up -d --build                    # Sobe todo o ambiente conteinerizado (app + infra)
```

---

## 3. Modo de Trabalho do Agente

- **Objetivo didático e clareza:** Antes de gerar arquivos grandes ou executar refatorações complexas, descreva o plano de ação em poucas linhas. Após concluir cada etapa ou fase, sintetize os conceitos de Kotlin, Spring, Kafka ou PostgreSQL empregados.
- **Governança de dependências:** Versões e bibliotecas estão centralizadas em `gradle/libs.versions.toml` (Version Catalog) e `build.gradle.kts`. Nunca altere versões sem justificativa e registro formal em ADR.
- **Rigor de testes:** Toda nova funcionalidade ou correção de bug deve vir acompanhada de testes automatizados. O comando `./gradlew test` deve passar com sucesso antes de considerar uma tarefa pronta.
- **Governança de Git:** Não execute comandos de `git commit` ou `git push` de forma autônoma. Mantenha as alterações limpas e organizadas no workspace para inspeção humana via `git diff` e `git status`.
- **Registro de decisões:** Toda decisão técnica relevante que desvie ou expanda o guia técnico deve ser registrada como um novo ADR em `docs/adr/`.

---

## 4. Arquitetura (Monólito Modular Hexagonal)

A estrutura de pacotes reside em `br.com.locasign`, dividida nos módulos de negócio: `lease`, `contract`, `notification` e o módulo transversal `shared`. Cada módulo respeita estritamente as quatro camadas arquiteturais:

1. **`domain` (Domínio puro):**
   - **Isolamento absoluto:** Não importa frameworks, bibliotecas de persistência ou clientes externos (sem Spring, Jackson, JDBC, Kafka ou referências à PandaDoc). Depende exclusivamente da biblioteca padrão do Kotlin e JDK.
2. **`app` (Aplicação / Casos de Uso):**
   - Depende unicamente de `domain`.
   - Os use cases são classes Kotlin puras, desacopladas de anotações de infraestrutura, instanciadas explicitamente em `infra/config`.
   - Demarcação transacional ocorre exclusivamente via porta `TransactionRunner` (conforme ADR-011).
3. **`interfaces` (Adaptadores Driving / Entrada):**
   - Controllers REST, handlers de eventos e DTOs de entrada.
   - Depende de `app` e `domain`. **Nunca depende de `infra`**.
4. **`infra` (Adaptadores Driven / Saída):**
   - Implementa as portas de saída definidas no domínio e na aplicação (repositórios JDBC, publishers de mensageria, integração com parceiros externos).
   - O adapter `pandadoc/` é o **único componente** do sistema que conhece endpoints, DTOs, tokens e estados específicos da PandaDoc. O restante da aplicação comunica-se estritamente através da abstração `SignatureProviderPort`.

---

## 5. Regras de Código e Boas Práticas

- **Nomenclatura e Glossário:** Siga rigorosamente a convenção da seção 4.1 do guia técnico (termos técnicos e código em inglês; vocabulário de negócio e documentação em português).
- **Kotlin Idiomático:**
  - **Proibido usar operador de asserção não-nula (`!!`).**
  - Prefira tipos selados (`sealed class` / `sealed interface`) com avaliações exaustivas em expressões `when` (sem branch `else` padrão).
- **Mapeamentos:** Escreva mapeamentos explícitos utilizando *extension functions* no pacote `mappers/`. Não utilize MapStruct ou bibliotecas mágicas de reflexão.
- **Bean Validation:** Anotações de validação (`@NotBlank`, `@NotNull`, etc.) em `data class` de DTOs devem utilizar o target `@field:` para garantir a visibilidade pelo Hibernate Validator (ex.: `@field:NotBlank`).
- **Invariantes e Agregado `Contract`:** Toda alteração de estado do contrato de locação deve passar obrigatoriamente pelo agregado `Contract`. Nunca atualize status diretamente no banco de dados.
- **Outbox Transacional:** Todo evento de domínio emitido deve ser persistido na tabela de outbox na mesma transação da entidade de negócio. Use cases nunca publicam diretamente no Kafka.
- **Inbox Transacional de Webhooks:**
  - Valide a assinatura HMAC calculada sobre os **bytes brutos do corpo da requisição (`raw bytes`)**, antes de qualquer parse.
  - Registre o payload na tabela de inbox e responda HTTP 200 imediatamente.
  - **Nunca responda HTTP 410** (a PandaDoc desativa webhooks que retornem 410).
  - Eventos desconhecidos ou não mapeados também devem responder HTTP 200.
- **Comunicação Assíncrona com PandaDoc:** Nenhuma chamada à API da PandaDoc pode ser executada dentro do ciclo de vida síncrono de uma requisição HTTP de usuário. Essas chamadas ocorrem em consumidores Kafka com limitador de taxa (*rate limiting*) e política de retentativa com DLT.
- **Idempotência:** Consumidores Kafka devem registrar o par `(consumer_group, event_id)` na tabela `processed_messages` na mesma transação em que o efeito colateral é executado.
- **Documentação da API Externa:** Antes de implementar integrações com a PandaDoc, consulte `https://developers.pandadoc.com/llms.txt` (adicionando `.md` ao final das URLs da documentação). Verifique as decisões já validadas no ADR-012.
- **Segurança e Proteção de Dados:** Nunca registre em logs segredos, chaves de API, tokens ou dados sensíveis de usuários (CPF e e-mail devem ser mascarados via `Cpf.masked()` e `Email.masked()`).
- **Migrações de Banco de Dados:** Scripts de migração do Flyway já aplicados são estritamente **imutáveis**. Qualquer ajuste ou correção estrutural exige uma nova migration versionada sequencialmente.
