# ADR-019: Portas de entrada para os adaptadores HTTP

- **Status:** proposta
- **Data:** 17/10/2026
- **Relação com o guia:** confirma `app/ports/in` só no limite HTTP (§3.3) e ajusta a decisão anterior de não ter porta
  de entrada
- **Origem:** a controller não deve depender da classe concreta do caso de uso nem da query

## Contexto

O desenho vigente, registrado no princípio DIP do `AGENTS.md` e em `docs/agents/arquitetura.md` §1, faz a controller, o
consumidor Kafka e o job injetarem a classe concreta e chamarem `execute`. `docs/tasks/plan-cqrs.md` também proíbe
`ports/in`.

A classe concreta continua sendo o único lugar da regra. O que muda é o adaptador HTTP: ele traduz a rota e não precisa
conhecer o construtor, as portas de saída nem os colaboradores do caso de uso. A porta de saída já separa `app` de
`infra`. A porta de entrada separa a controller da classe que implementa a operação.

## Decisão

Cada operação chamada por controller HTTP ganha uma porta em `app/ports/in`. A classe concreta a implementa. O `@Bean`
continua devolvendo a classe concreta, e o Spring injeta a porta na controller porque a classe a implementa.

Portas:

| Porta                        | Método                                      | Implementação            |
|------------------------------|---------------------------------------------|--------------------------|
| `RequestContractPort`        | `execute(command): ContractId`              | `RequestContract`        |
| `CancelContractPort`         | `execute(command)`                          | `CancelContract`         |
| `ReconcileContractPort`      | `executeFor(id): ReconciledContractSummary` | `ReconcileContracts`     |
| `GetContractPort`            | `execute(id): ContractDetailView`           | `GetContract`            |
| `GetContractHistoryPort`     | `execute(id): List<HistoryEntryView>`       | `GetContractHistory`     |
| `ReceiveProviderWebhookPort` | `execute(command): WebhookReceipt`          | `ReceiveProviderWebhook` |
| `RegisterLeasePort`          | `execute(command): LeaseId`                 | `RegisterLease`          |
| `GetLeasePort`               | `execute(leaseId): LeaseDetailView`         | `GetLease`               |

O comando e o resultado que a controller monta ou lê ficam no arquivo da porta: `RequestContractCommand`,
`CancelContractCommand`, `ReconciledContractSummary`, `ReceiveProviderWebhookCommand`, `WebhookReceipt` e
`RegisterLeaseCommand`. As views de leitura permanecem em `app/queries`.

`ReconcileContractPort` expõe só `executeFor`. `executeStale()` continua na classe, usada pelo `ReconciliationJob`.

Ficam de fora, injetando a classe concreta: consumidores Kafka, jobs `@Scheduled` e qualquer caso de uso que nenhuma
controller chama. `AgencySignerDefaults` permanece junto de `RegisterLease`.

`HexagonalArchitectureTest` impede que `interfaces/web` e `interfaces/webhook` dependam de `app.usecases` ou das classes
`GetContract`, `GetContractHistory` e `GetLease`. `interfaces/messaging` não entra nessa regra.

## Consequências

- A controller depende de `app/ports/in`, de value objects de domínio e, no mapper, das views de leitura.
- O teste de fatia web mocka a porta. O teste do caso de uso continua instanciando a classe concreta.
- Uma porta nova só nasce junto com uma controller que a chame. Kafka e jobs não ganham porta por simetria.
- O pacote se chama `ports.in`. Como `in` é palavra reservada, o nome no código usa crase: `` ports.`in` ``.
