# LocaSign: regras para o Claude Code

Serviço backend que automatiza o ciclo de vida de contratos de locação residencial: gera o contrato,
envia para assinatura eletrônica na PandaDoc, acompanha tudo por webhooks e dispara as ações
pós-assinatura. É um **projeto de estudo** de Kotlin, PostgreSQL, Kafka e Docker.

## Leitura obrigatória antes de qualquer tarefa

1. `docs/plano-de-negocio.md`: o quê e o porquê. As regras R1 a R10 prevalecem sobre tudo.
2. `docs/arquitetura-tecnica.md`: o como (contratos, nomes, decisões).
3. `docs/adr/`: decisões tomadas depois do guia, incluindo desvios dele.

## Comandos

```bash
docker compose up -d                                          # postgres + kafka
./gradlew bootRun --args='--spring.profiles.active=local'     # app pela IDE/terminal
./gradlew build                                               # compila e roda os testes
docker compose --profile app up -d --build                    # tudo em containers
```

## Como trabalhar

- Este é um projeto de estudo: antes de gerar arquivos grandes, descreva o plano em poucas linhas;
  depois de cada fase, resuma os conceitos de Kotlin, Spring, Kafka ou PostgreSQL usados.
- As versões ficam em `gradle/libs.versions.toml` e no `build.gradle.kts`. Não altere sem um ADR.
- Commits pequenos, um por capacidade de negócio. Rode `./gradlew test` antes de cada commit.
- Toda decisão técnica relevante que mudar algo do guia vira um ADR em `docs/adr/`.

## Arquitetura (hexagonal, monólito modular)

Módulos em `br.com.locasign`: `lease`, `contract`, `notification` e `shared`. Cada um segue
`domain`, `app`, `interfaces` (driving) e `infra` (driven).

- `domain` **não importa** Spring, Jackson, JDBC, Kafka nem nada da PandaDoc. Só a biblioteca padrão.
- `app` depende apenas de `domain`. Os use cases são classes Kotlin puras, instanciadas em
  `infra/config`. A transação entra pela porta `TransactionRunner` (ADR-011).
- `interfaces` depende de `app` e `domain`, nunca de `infra`.
- `infra` implementa as portas de saída. O adapter `pandadoc/` é o único lugar que conhece DTOs,
  status e endpoints da PandaDoc; o resto fala com `SignatureProviderPort`.

## Regras de código

- Nomes de código seguem o glossário da seção 4.1 do guia (inglês no código, português no negócio).
- **Não usar `!!`.** Preferir tipos selados com `when` exaustivo sem `else`.
- Mapeamentos com extension functions em `mappers/`. Não usar MapStruct.
- Anotações de Bean Validation em DTOs sempre com `@field:` (para o Hibernate Validator enxergá-las).
- **Toda mudança de status passa pelo agregado `Contract`.** Nunca atualizar o status direto no banco.
- **Todo evento sai pelo outbox.** Use cases nunca publicam direto no Kafka.
- **Todo webhook entra pelo inbox:** validar o HMAC sobre o **corpo bruto (bytes)**, gravar no inbox e
  responder 200 rápido. **Nunca responder 410** (a PandaDoc desativa o webhook). Eventos
  desconhecidos também recebem 200.
- Nenhuma chamada à PandaDoc dentro de uma requisição HTTP do usuário: ela acontece nos
  consumidores Kafka, com limitador de taxa e retentativa.
- Tudo é idempotente: consumidores registram `(grupo, eventId)` em `processed_messages` na mesma
  transação do efeito.
- Antes de implementar uma chamada à PandaDoc, consulte `https://developers.pandadoc.com/llms.txt`
  (acrescentar `.md` às páginas). Itens "(confirmar)" do guia já foram verificados: ver ADR-012.
- Nunca registrar em log segredos, nem CPF ou e-mail completos (use `Cpf.masked()` e `Email.masked()`).
- Migrations Flyway aplicadas **nunca** são editadas; correções viram uma nova migration.
