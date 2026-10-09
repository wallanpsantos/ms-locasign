# Architecture Blueprint — Arquitetura Hexagonal + Clean Architecture + DDD

> **Blueprint de referência | Java / Kotlin | Spring Boot na borda | Orientação por feature/subdomínio**  
> **Origem:** `Template de Arquitetura de Software.txt`, disponibilizado pelo solicitante.  
> **Escopo:** especificação de organização, contratos, dependências, fluxos, qualidade e governança. **Não** é um
> projeto executável pronto nem uma prova de conformidade de uma implementação.

## 1. Objetivo e princípios

Organizar o sistema com um **Core** independente de tecnologias e adaptadores que se conectam ao Core por **ports**. O
princípio fundamental é que as **dependências de código apontem para dentro**, enquanto as chamadas em tempo de execução
podem seguir em diferentes direções. Inspirado na imagem fornecida: *Driving/Primary Adapters* à esquerda;
*Driven/Secondary Adapters* à direita; *Domain + Application* no núcleo.

### Invariantes de arquitetura

- **ARQ-01 — Independência do domínio:** `product.domain` não depende de `app`, `interfaces`, `infra`, Spring, JPA,
  HTTP, Feign, Kafka ou implementação de banco.
- **ARQ-02 — Responsabilidade da aplicação:** `product.app` orquestra os casos de uso, chama operações de domínio,
  define portas e modelos de entrada/saída independentes de transporte. Depende apenas de `domain` e de código
  próprio/abstrações neutras definidas na aplicação.
- **ARQ-03 — Entrada:** `product.interfaces` contém adaptadores HTTP, mensageria consumidora e CLI (se necessário);
  traduz protocolos para comandos/queries e invoca casos de uso.
- **ARQ-04 — Saída:** `product.infra` implementa as portas de saída e contém JPA, Feign/HTTP, produtores de mensagens e
  respectivas classes técnicas.
- **ARQ-05 — Composição:** configurações Spring conectam portas a adaptadores; nenhuma inversão de dependência é obtida
  apenas por nomes de diretórios.
- **ARQ-06 — Domínio rico:** regras e invariantes permanecem em Entities, Aggregates, Value Objects e Domain Services,
  evitando objetos puramente anêmicos onde há comportamento de negócio.
- **ARQ-07 — Contratos nas fronteiras:** DTOs de REST/Kafka/Feign/JPA ficam nos respectivos adaptadores; entradas e
  saídas de casos de uso devem ser neutras em relação a frameworks.
- **ARQ-08 — Qualidade verificável:** testes unitários, de integração, de contrato e de arquitetura devem validar essas
  regras no CI.

> **Obrigatório vs opcional:** as regras de independência e isolamento de responsabilidades são fundamentais; `ports/in`
> separadas, CQRS, transactional outbox, vários módulos Gradle/Maven e CLI são escolhas arquiteturais condicionadas aos
> requisitos. A imagem ilustra alternativas, não obriga a implementar todas.

## 2. Estrutura de diretórios recomendada (comentários preservados e ampliados)

A nomenclatura `domain / app / interfaces / infra` do original **foi preservada** para evitar uma migração cosmética.
Comentários originais aparecem nas pastas correspondentes, e notas `BLUEPRINT:` esclarecem refinamentos e pontos de
atenção. A íntegra literal do template original está no **Apêndice A**.

```text
/ (Raiz do repositório)
├── .github/                         # Pipelines CI/CD (ex: workflows do GitHub Actions)
│   └── workflows/
│       └── ci.yml                   # BLUEPRINT: compila, testa, valida arquitetura e publica relatórios; etapas ajustadas ao projeto
├── docs/                            # BLUEPRINT: decisões e contratos arquiteturais versionados
│   ├── architecture/
│   │   ├── README.md                # BLUEPRINT: contexto, fronteiras, regras e diagramas
│   │   └── dependency-rules.md      # BLUEPRINT: matriz de dependência e exceções aprovadas
│   └── adr/                         # BLUEPRINT: Architecture Decision Records
├── gradle/                           # BLUEPRINT: wrapper se Gradle for o build tool escolhido; Maven é alternativa válida
├── build.gradle.kts                  # BLUEPRINT: compilação, testes, linters, plugins e dependências
├── settings.gradle.kts               # BLUEPRINT: projeto e, opcionalmente, módulos para reforço no compile-time
├── Dockerfile                        # Receita de infraestrutura do contêiner
├── docker-compose.yml                # Dependências locais para dev (Postgres, Kafka, Redis)
├── README.md                         # BLUEPRINT: instruções de execução, limites e decisões
└── src/
    ├── main/
    │   ├── java/br/com/projeto/demo/ # BLUEPRINT: Java. Para Kotlin, substituir 'java' por 'kotlin'; em projeto misto, ambos.
    │   │   └── product/              # Módulo/Feature: product (Arquitetura orientada a subdomínios)
    │   │       │
    │   │       ├── domain/           # 1. CORE: Coração do software (NÃO PODE importar NADA de app, interfaces ou infra)
    │   │       │   ├── models/       # Entidades ricas e Agregados. Ex: Product.java (contém métodos de negócio como updatePrice())
    │   │       │   ├── valueobjects/ # Objetos imutáveis que não têm identidade própria. Ex: Sku.java, Money.java
    │   │       │   ├── exceptions/   # Exceções de violação de regra de negócio. Ex: ProductAlreadyDiscontinuedException.java
    │   │       │   ├── services/     # Regras que envolvem múltiplas entidades e não cabem em uma só. Ex: ProductDiscountService.java
    │   │       │   └── events/       # Definição de eventos do domínio (interfaces/records puros). Ex: ProductCreatedDomainEvent.java
    │   │       │                    # BLUEPRINT: não confundir evento de domínio com payload/evento de integração.
    │   │       │
    │   │       ├── app/              # 2. ORQUESTRAÇÃO: Coordena o fluxo, mas não dita a regra de negócio (Depende apenas do 'domain')
    │   │       │   ├── usecases/     # Casos de uso orientados à intenção do usuário (1 classe = 1 ação). Ex: CreateProductUseCase.java
    │   │       │   ├── commands/     # BLUEPRINT: entrada sem HTTP/Kafka; ex.: CreateProductCommand.java
    │   │       │   ├── queries/      # BLUEPRINT: entrada de consultas; ex.: GetProductQuery.java
    │   │       │   ├── results/      # BLUEPRINT: resultados de casos de uso sem ResponseEntity nem DTO de transporte
    │   │       │   └── ports/        # Portas (Interfaces) que definem o que a aplicação precisa do mundo externo
    │   │       │       ├── in/       # (Opcional) Contratos de entrada que os Use Cases implementam.
    │   │       │       │            # BLUEPRINT: recomendável quando se deseja desacoplar consumidores da classe concreta.
    │   │       │       └── out/      # Portas de SAÍDA (Outbound)
    │   │       │           ├── repository/  # Contratos para salvar/buscar do banco. Ex: ProductRepositoryPort.java
    │   │       │           ├── integration/ # Contratos para buscar dados em outras APIs. Ex: TaxCalculationPort.java
    │   │       │           ├── messaging/   # Contratos para avisar o mundo externo. Ex: ProductEventPublisherPort.java
    │   │       │           └── query/       # BLUEPRINT (opcional): projeções de leitura por portas, sem necessidade de Aggregate inteiro
    │   │       │
    │   │       ├── interfaces/       # 3. DRIVING ADAPTERS: O mundo externo chamando a nossa aplicação (Entrada)
    │   │       │   ├── web/          # Comunicação HTTP REST
    │   │       │   │   ├── controllers/  # Recebe requisição, valida (Bean Validation), chama o Use Case e retorna status HTTP.
    │   │       │   │   ├── dto/          # Objetos anêmicos de transferência (Ideal usar Java Records)
    │   │       │   │   │   ├── request/  # Payloads recebidos. Ex: CreateProductRequest.java
    │   │       │   │   │   └── response/ # Payloads devolvidos. Ex: ProductDetailResponse.java
    │   │       │   │   ├── mappers/      # Conversores (ex: MapStruct). Transforma DTO <-> Domain Model.
    │   │       │   │   │                # BLUEPRINT: preferir DTO -> Command/Query e Result -> DTO; conversão direta ao Domain somente quando justificada.
    │   │       │   │   ├── advice/       # Interceptadores de erro (@RestControllerAdvice). Converte exceções do 'domain' em HTTP 400/404/422.
    │   │       │   │   │                # BLUEPRINT: classificar por semântica; 409 para conflito, 404 não encontrado, etc.; não fixar regra genérica.
    │   │       │   │   └── swagger/      # Interfaces apenas com anotações do OpenAPI para limpar os Controllers.
    │   │       │   ├── messaging/        # Comunicação Assíncrona de Entrada (Consumo)
    │   │       │   │   ├── consumers/   # Listeners de Kafka/RabbitMQ. Ex: CategoryUpdatedKafkaListener.java
    │   │       │   │   ├── dto/         # Formato da mensagem recebida do tópico.
    │   │       │   │   └── mappers/     # Converte Mensagem Kafka -> Chamada para o Use Case.
    │   │       │   │                   # BLUEPRINT: validar schema, idempotência, reentrega e tratamento de falhas.
    │   │       │   └── cli/            # BLUEPRINT (opcional): comandos de linha de comando / jobs que invocam casos de uso
    │   │       │
    │   │       └── infra/            # 4. DRIVEN ADAPTERS: Nossa aplicação chamando o mundo externo (Saída) + Configurações Spring
    │   │           ├── persistence/   # Tudo relacionado a Banco de Dados
    │   │           │   ├── entities/    # Classes atreladas ao framework ORM. Ex: ProductJpaEntity.java (@Entity, @Table)
    │   │           │   ├── repositories/# Interfaces do Spring Data JPA. Ex: SpringDataProductRepository.java
    │   │           │   ├── adapters/    # Implementa o 'app/ports/out/repository'. Injeta o Spring Data e faz a ponte.
    │   │           │   └── mappers/     # Converte Domain Model <-> JpaEntity. (O banco não conhece o domínio, o domínio não conhece o banco).
    │   │           │                    # BLUEPRINT: a dependência é do adapter para o modelo de domínio, nunca o inverso.
    │   │           │
    │   │           ├── feign/         # Integrações HTTP com outros serviços (ex: chamando a API de Taxas)
    │   │           │   ├── clients/     # Interfaces do Feign (@FeignClient).
    │   │           │   ├── dto/         # Objetos específicos desta API externa para não acoplar com a web/dto
    │   │           │   │   ├── request/ # Payload que vamos enviar para a API terceira.
    │   │           │   │   └── response/# Payload que a API terceira nos devolve.
    │   │           │   ├── adapters/    # Implementa o 'app/ports/out/integration'. Chama o Feign e mapeia o resultado.
    │   │           │   ├── mappers/     # Converte Feign DTO <-> Domain Model.
    │   │           │   │                # BLUEPRINT: priorizar contratos da porta; tipos externos não vazam para app/domain.
    │   │           │   └── exceptions/  # ErrorDecoders do Feign para tratar timeouts, 404s e 500s da API externa.
    │   │           │                    # BLUEPRINT: traduzir erros externos para falhas de integração próprias da aplicação.
    │   │           │
    │   │           ├── messaging/     # Publicação de Mensagens
    │   │           │   ├── publishers/ # Implementa o 'app/ports/out/messaging'. Injeta KafkaTemplate e envia a mensagem.
    │   │           │   ├── dto/        # O formato do evento que será serializado para JSON/Avro e enviado ao tópico.
    │   │           │   └── mappers/    # Converte Domain Event <-> Kafka DTO.
    │   │           │                    # BLUEPRINT: usar outbox quando consistência entre gravação e publicação exigir.
    │   │           │
    │   │           └── config/        # Toda a "mágica" do framework fica isolada aqui
    │   │               ├── beans/     # Classes @Configuration que instanciam os UseCases injetando os adapters da infraestrutura.
    │   │               ├── security/  # Configurações de OAuth2, JWT, CORS.
    │   │               ├── kafka/     # Configuração de Producers, Consumers, Tópicos, DLQs e retentativas.
    │   │               ├── database/  # Migrations (Flyway/Liquibase), Configuração de Datasource secundário, etc.
    │   │               └── swagger/   # Configuração geral da documentação da API.
    │   └── resources/
    │       ├── application.yml        # BLUEPRINT: variáveis e configurações técnicas, sem segredos hardcoded
    │       └── db/migration/          # BLUEPRINT: scripts Flyway, ou estrutura equivalente do Liquibase
    └── test/
        ├── java/br/com/projeto/demo/product/  # BLUEPRINT: para Kotlin usar test/kotlin; manter espelhamento por feature
        │   ├── domain/               # BLUEPRINT: invariantes, Value Objects, Aggregates e Domain Services, sem Spring
        │   ├── app/                  # BLUEPRINT: fluxos com fakes/stubs de portas, sem banco real
        │   ├── interfaces/           # BLUEPRINT: contratos HTTP e Kafka consumer
        │   ├── infra/                # BLUEPRINT: JPA, Feign, serialização, mensageria, Testcontainers quando pertinente
        │   └── architecture/         # BLUEPRINT: ArchUnit e políticas automatizadas de dependência
        └── resources/                # BLUEPRINT: fixtures e configurações de testes
```

**Notas importantes de leitura:**

1. Os caminhos `src/main/java` e `src/test/java` são **variantes Java**. Para Kotlin puro, usar `src/main/kotlin` e
   `src/test/kotlin`, com a mesma topologia de packages. Não colocar o mesmo arquivo nos dois caminhos.
2. No template original, `models/` reúne Entities e Aggregates. É válido; criar subpastas adicionais só quando houver
   ganho claro.
3. Os comentários originais referentes a mapper REST/Feign refletem a intenção original; as notas `BLUEPRINT:` registram
   a recomendação de usar contratos da aplicação, sem apagar o texto fornecido.
4. `app/ports/in` pode ficar vazio/ser omitido quando a classe concreta do Use Case for um ponto de entrada aceito. Em
   um padrão organizacional mais rígido, explicitar uma interface de entrada costuma facilitar governança.
5. `config/database` é destinado a configurações. Os scripts SQL/DDL de migração geralmente ficam em
   `src/main/resources/db/migration`, conforme Flyway/Liquibase.

## 3. Matriz de dependências permitidas

| Origem                            | Pode depender de                                                  | Não deve depender de                                                         |
|-----------------------------------|-------------------------------------------------------------------|------------------------------------------------------------------------------|
| `domain`                          | biblioteca padrão e bibliotecas neutras aprovadas                 | `app`, `interfaces`, `infra`, Spring, JPA, Feign, Kafka                      |
| `app`                             | `domain`, interfaces/tipos próprios da aplicação                  | controllers, requests HTTP, entidades JPA, Feign, Kafka, configuração Spring |
| `interfaces`                      | `app` e tipos do `domain` apenas quando necessários na fronteira  | implementação de persistência/Feign/Kafka publisher                          |
| `infra`                           | `app`, `domain`, frameworks e clientes técnicos                   | controllers HTTP ou DTOs do adaptador de entrada                             |
| `infra.config` (composition root) | portas, casos de uso, adaptadores concretos e configuração Spring | regras de negócio novas                                                      |

**Interpretação:** a aplicação **usa** suas portas de saída; a infraestrutura **implementa** essas portas. Em tempo de
execução, a chamada sai do Use Case para a interface de saída e é despachada ao adaptador concreto.

### Contratos de fronteira

- `web.dto.request` → `app.commands` / `app.queries` → Use Case → `app.results` → `web.dto.response`.
- `messaging.dto` (entrada) → validação/tradução → Command → Use Case.
- Use Case → `ports.out.repository` → `infra.persistence.adapters` → JPA.
- Use Case → `ports.out.integration` → `infra.feign.adapters` → Feign/client externo.
- Use Case → `ports.out.messaging` → `infra.messaging.publishers` → Kafka/RabbitMQ.
- Consulta simples pode usar `Query Use Case` → `Read Port` → projeção de leitura, sem reconstruir um Aggregate. **Não**
  habilita controller → banco diretamente.

## 4. Fluxos de referência

### 4.1 Command com regra de negócio

```text
HTTP POST /products
  -> interfaces.web.controllers.ProductController
  -> interfaces.web.mappers.CreateProductRequest -> app.commands.CreateProductCommand
  -> app.ports.in.CreateProductUseCase [quando esta porta for adotada]
  -> app.usecases.CreateProductService
  -> domain.models.Product.create(...)  [invariantes e Value Objects]
  -> app.ports.out.repository.ProductRepositoryPort
  -> infra.persistence.adapters.ProductPersistenceAdapter
  -> Spring Data JPA -> DB
  -> app.results.CreateProductResult -> interfaces.web.dto.response
```

### 4.2 Query (alternativa CQRS leve, opcional)

```text
HTTP GET /products/{id}
  -> Controller -> app.queries.GetProductQuery
  -> GetProductQueryUseCase -> app.ports.out.query.ProductReadPort
  -> JPA/SQL read adapter -> app.results.ProductView
  -> HTTP response DTO
```

### 4.3 Evento externo e evento de domínio

```text
Kafka/RabbitMQ (mensagem de entrada)
  -> interfaces.messaging.consumers
  -> mapper/validação do schema
  -> app.command + Use Case
  -> Aggregate / Domain Event (sem dependência Kafka)
  -> porta de saída de publicação OU registro de outbox
  -> infra.messaging.publisher -> broker
```

**Garantias a decidir em ADR:** at-least-once, idempotência, DLQ, retries, versionamento, ordenação, consistência
transacional. Transactional Outbox é uma opção quando a gravação no banco precisa ser consistente com a publicação
assíncrona; não é obrigação de todo sistema.

## 5. Convenções Java e Kotlin

### Java — portas e modelo da aplicação (sem imports Spring)

```java
// product/app/ports/in/CreateProductUseCase.java
public interface CreateProductUseCase {
    CreateProductResult execute(CreateProductCommand command);
}

// product/app/commands/CreateProductCommand.java
public record CreateProductCommand(String sku, java.math.BigDecimal price) {
}

// product/app/results/CreateProductResult.java
public record CreateProductResult(String id) {
}

// product/app/ports/out/repository/ProductRepositoryPort.java
public interface ProductRepositoryPort {
    Product save(Product product); // Product é entidade de domain.models
}

// product/app/usecases/CreateProductService.java
public final class CreateProductService implements CreateProductUseCase {
    private final ProductRepositoryPort products;

    public CreateProductService(ProductRepositoryPort products) {
        this.products = products;
    }

    @Override
    public CreateProductResult execute(CreateProductCommand command) {
        // Exemplo ilustrativo: criar entidade pelo factory/método do domínio,
        // validar invariantes, persistir por uma porta e retornar um result neutro.
        // Não usar @Transactional/@Service nem ResponseEntity na implementação estrita.
        throw new UnsupportedOperationException("Exemplo conceitual; implementar conforme o domínio");
    }
}
```

### Kotlin — porta e modelo da aplicação (sem imports Spring)

```kotlin
// product/app/ports/in/CreateProductUseCase.kt
interface CreateProductUseCase {
    fun execute(command: CreateProductCommand): CreateProductResult
}

// product/app/commands/CreateProductCommand.kt
data class CreateProductCommand(val sku: String, val price: java.math.BigDecimal)

// product/app/results/CreateProductResult.kt
data class CreateProductResult(val id: String)

// product/app/ports/out/repository/ProductRepositoryPort.kt
interface ProductRepositoryPort {
    fun save(product: Product): Product // Product de domain.models
}

// product/app/usecases/CreateProductService.kt
class CreateProductService(
    private val products: ProductRepositoryPort
) : CreateProductUseCase {
    override fun execute(command: CreateProductCommand): CreateProductResult {
        // Exemplo ilustrativo: regras e invariantes ficam no Aggregate.
        // Não depender de @Service/@Transactional no núcleo estrito.
        TODO("Implementar conforme o domínio")
    }
}
```

**Implementação de adaptadores:** `ProductJpaEntity` contém `@Entity`; `ProductPersistenceAdapter` implementa
`ProductRepositoryPort`, faz mapeamento entre JPA e domínio, e recebe um `SpringDataProductRepository`.
`ProductController` recebe `CreateProductUseCase`, não `ProductPersistenceAdapter`. O bean deve ser conectado em
`infra.config.beans`.

**Transações:** decidir em ADR onde aplicar a fronteira transacional (ex.: decorador AOP configurado na composição,
interceptor ou outro mecanismo). Evitar `@Transactional` no domínio e não deixar uma operação multi-etapa atravessar
transações inconsistentes por acidente.

## 6. Critérios de qualidade e testes arquiteturais

### Política sugerida de ArchUnit (exemplo Java)

```java
// Aplicar as regras ao package real. É exemplo de política estrita e NÃO executa isoladamente:
// exige dependência de teste ArchUnit e configuração do build.
@AnalyzeClasses(packages = "br.com.projeto.demo")
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_independent = noClasses()
            .that().resideInAPackage("..product.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..product.app..", "..product.interfaces..", "..product.infra..",
                    "org.springframework..", "jakarta.persistence.."
            );

    @ArchTest
    static final ArchRule app_independent_of_adapters = noClasses()
            .that().resideInAPackage("..product.app..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..product.interfaces..", "..product.infra..",
                    "org.springframework..", "jakarta.persistence.."
            );
}
```

Imports necessários no teste: `com.tngtech.archunit.junit.AnalyzeClasses`, `com.tngtech.archunit.junit.ArchTest`,
`com.tngtech.archunit.lang.ArchRule`, `static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses`. Regras
adicionais podem verificar ausência de DTOs de adaptadores nas portas, proibição de chamadas diretas do controller a
`infra.persistence` e pacotes por feature.

### Estratégia de testes por camada

| Teste                             | O que verifica                                                    | Dependências                                 |
|-----------------------------------|-------------------------------------------------------------------|----------------------------------------------|
| Domínio                           | invariantes, Value Objects, Aggregate e Domain Services           | sem contexto Spring                          |
| Aplicação                         | orquestração, portas, erros de negócio, casos de uso              | fakes/stubs de portas                        |
| Entrada HTTP/messaging            | mapeamento, validação de request/schema, status e observabilidade | testes de fatia/contratos                    |
| Saída persistence/Feign/messaging | mapeamentos, queries, falhas externas, serialização               | integração / Testcontainers quando aplicável |
| Arquitetura                       | direções das dependências e frameworks fora do núcleo             | ArchUnit e/ou módulos de compilação          |

## 7. Segurança, resiliência e observabilidade

- **Segurança:** autenticação OAuth2/JWT/CORS é técnica e fica na borda; políticas de autorização de negócio pertencem
  ao caso de uso/domínio, quando aplicável.
- **Resiliência:** timeout, retry com backoff, circuit breaker e fallback são políticas do adaptador ou da integração,
  conforme contrato e idempotência.
- **Observabilidade:** `traceId`/`correlationId`, logs estruturados, métricas e tracing ficam nas bordas ou em aspectos
  transversais, sem acoplar o domínio a bibliotecas de telemetria.
- **Dados e configuração:** segredos via variáveis/secret manager; migrations versionadas; DTOs de integração não vazam
  para domínio.
- **Evolução:** pacotes por feature (por exemplo, `product`, `order`) não autorizam acesso direto às classes de infra
  privadas de outras features; interfaces explícitas são preferíveis.

## 8. Architecture Decision Records (ADRs) sugeridos

- `ADR-001`: diretório único versus múltiplos módulos Gradle/Maven.
- `ADR-002`: portas de entrada explícitas (`ports/in`) ou Use Cases concretos.
- `ADR-003`: posicionamento da transação sem Spring no domínio.
- `ADR-004`: estratégia de leitura (Aggregate tradicional vs. projection/query port/CQRS leve).
- `ADR-005`: consistência de eventos, outbox, deduplicação e DLQ.
- `ADR-006`: nomenclatura de `interfaces` e `infra` vs. `adapter/in`, `adapter/out`.
- `ADR-007`: contratos e classificação de erros HTTP/integração.

## 9. Definition of Done arquitetural

- [ ] Nenhum import ou dependência de adaptadores/framework no `domain`.
- [ ] Nenhum DTO de REST, Kafka, Feign ou entidade JPA atravessa contratos do `app`.
- [ ] `interfaces` traduz protocolo em Command/Query e resultado em DTO, sem regra de negócio.
- [ ] `infra` implementa portas do `app` e concentra tecnologia externa.
- [ ] Invariantes de negócio estão cobertas por testes unitários de domínio.
- [ ] Casos de uso estão cobertos por testes com portas falsas/stubs.
- [ ] Adaptadores de entrada/saída possuem testes proporcionais ao risco.
- [ ] Testes de arquitetura falham no CI diante de imports proibidos.
- [ ] Fronteiras transacionais, falhas externas e políticas de reentrega estão documentadas.
- [ ] Escolhas opcionais foram justificadas em ADRs, sem complexidade desnecessária.

## 10. Níveis de conclusão

**Conforme ao desenho:** a topologia é coerente com Arquitetura Hexagonal, Clean Architecture e princípios selecionados
de DDD. **Conforme em código:** somente após inspeção dos imports, das dependências, do comportamento e da suíte de
testes. **Pronto para produção:** exige ainda os requisitos de segurança, operação, confiabilidade e negócio específicos
do contexto.

---

_Fim do Architecture Blueprint._
