# Implementation Plan: Redocumentação Estrutural de Classes (KDoc) no LocaSign

## Overview

Este plano estabelece o processo de substituição completa das descrições existentes e adição de novas descrições
padronizadas para cada classe, interface, objeto e enumeração presente na base de código do microsserviço `ms-locasign`.
O objetivo é fornecer documentação técnica idiomática em Kotlin (KDoc), em português brasileiro (pt-BR), com foco
estrito em **o que a classe faz** (seu comportamento e contexto no fluxo de negócio) e **sua responsabilidade**
(princípio de responsabilidade única, limites arquiteturais hexagonais e invariantes mantidos).

## Architecture Decisions

- **ADR-KDOC-001 - Padrão Estrutural KDoc com Seção Explícita de Responsabilidade:**
    - Toda classe, interface, enum, sealed type ou object receberá um bloco KDoc no topo de sua declaração com a
      seguinte estrutura padronizada:
      ```kotlin
      /**
       * [Explicação concisa e contextualizada sobre o que a classe faz tecnicamente e no negócio].
       *
       * **Responsabilidade:**
       * - [Responsabilidade técnica/arquitetural específica dentro da camada hexagonal].
       * - [Invariantes, garantias, regras de negócio ou contratos que ela assegura].
       */
      ```
- **ADR-KDOC-002 - Alinhamento com as Camadas Hexagonais:**
    - As descrições devem refletir com precisão a camada à qual a classe pertence (`domain`, `app`, `interfaces`,
      `infra`), referenciando as regras de negócio R1 a R10 e os ADRs vigentes (ex.: isolamento de domínio, demarcação
      via `TransactionRunner`, idempotência via `ProcessedMessagesPort`, outbox transacional, etc.).
- **ADR-KDOC-003 - Idioma e Tom Técnico:**
    - Comentários exclusivamente em português brasileiro (pt-BR), diretos, sem adjetivos subjetivos ("o melhor",
      "robusto", "perfeito"), mantendo os termos de código e tecnologia em inglês (`Spring Boot`, `Kafka`, `PandaDoc`,
      `PostgreSQL`, etc.).
- **ADR-KDOC-004 - Não Alteração de Lógica Executável:**
    - As mudanças serão estritamente restritas a comentários e KDocs. Nenhuma lógica, assinatura de método, anotação ou
      importação funcional será alterada, garantindo total estabilidade do sistema e aprovação contínua na compilação do
      Gradle.

## Task List

### Phase 1: Módulo Shared (Fundação Transversal)

- [ ] Task 1: Documentar Domínio Compartilhado (Value Objects e Exceções)
- [ ] Task 2: Documentar Aplicação Compartilhada (Portas de Infraestrutura - Parte 1)
- [ ] Task 3: Documentar Aplicação Compartilhada (Portas de Infraestrutura - Parte 2 e Mensageria)
- [ ] Task 4: Documentar Infraestrutura Compartilhada (Propriedades e Configurações de Beans)
- [ ] Task 5: Documentar Infraestrutura Compartilhada (Observabilidade, Agendamento e Mensageria)
- [ ] Task 6: Documentar Infraestrutura Compartilhada (Persistência e Adaptadores JDBC)
- [ ] Task 7: Documentar Interfaces Compartilhadas (Web, Filtros, Tratamento de Erros e Segurança)

### Checkpoint: Shared Module

- [ ] Compilação do módulo e projeto com `./gradlew compileKotlin` sem erros
- [ ] Revisão de aderência aos padrões de KDoc em todos os 30 arquivos de `shared`

### Phase 2: Módulo Lease (Ciclo de Vida de Locação)

- [ ] Task 8: Documentar Domínio de Locação (Agregado Lease, Partes e Value Objects)
- [ ] Task 9: Documentar Aplicação de Locação (Casos de Uso, Portas de Saída e Visões)
- [ ] Task 10: Documentar Interfaces Web de Locação (Controller REST, DTOs, Mappers e OpenAPI)
- [ ] Task 11: Documentar Infraestrutura de Locação (Configuração, Entidades, Mappers e Repositório JDBC)

### Checkpoint: Lease Module

- [ ] Compilação do projeto com `./gradlew compileKotlin` sem erros
- [ ] Revisão de aderência aos padrões de KDoc em todos os 19 arquivos de `lease`

### Phase 3: Módulo Notification (Comunicação de Eventos)

- [ ] Task 12: Documentar Domínio e Aplicação de Notificações (Política, Portas e Casos de Uso)
- [ ] Task 13: Documentar Infraestrutura e Interfaces de Notificações (Configuração, Adaptadores JDBC e Consumidor
  Kafka)

### Checkpoint: Notification Module

- [ ] Compilação do projeto com `./gradlew compileKotlin` sem erros
- [ ] Revisão de aderência aos padrões de KDoc em todos os 6 arquivos de `notification`

### Phase 4: Módulo Contract - Domínio e Aplicação

- [ ] Task 14: Documentar Domínio de Contratos (Agregado Contract, Status e Value Objects)
- [ ] Task 15: Documentar Domínio de Contratos (Partes, Serviços de Transição e Eventos de Domínio)
- [ ] Task 16: Documentar Portas de Saída e Visões de Contratos (Integração e Modelos de Provedor)
- [ ] Task 17: Documentar Portas de Saída e Visões de Contratos (Mensageria, Repositório e Visões de Consulta)
- [ ] Task 18: Documentar Casos de Uso de Contratos (Criação, Emissão e Orquestração Inicial)
- [ ] Task 19: Documentar Casos de Uso de Contratos (Webhooks, Arquivamento e Pós-Assinatura)
- [ ] Task 20: Documentar Casos de Uso de Contratos (Cancelamento, Expiração, Reconciliação e Lembretes)

### Checkpoint: Contract Domain and Application

- [ ] Compilação do projeto com `./gradlew compileKotlin` sem erros
- [ ] Revisão de aderência aos padrões de KDoc em domínio e aplicação do módulo `contract`

### Phase 5: Módulo Contract - Infraestrutura

- [ ] Task 21: Documentar Integração PandaDoc (Cliente HTTP, Rate Limiter, Erros e Mappers)
- [ ] Task 22: Documentar Integração PandaDoc (Adaptador de Assinatura, Gateway de Webhook e DTOs)
- [ ] Task 23: Documentar Persistência de Contratos (Entidades, Mappers, Repositório JDBC e Adaptador Principal)
- [ ] Task 24: Documentar Persistência e Storage de Contratos (Adapters de Consulta, Bridge de Locação e Arquivamento em
  Disco)
- [ ] Task 25: Documentar Mensageria, Agendamento e Configurações de Contratos (Publisher Outbox, Payloads, Jobs e
  Beans)

### Checkpoint: Contract Infrastructure

- [ ] Compilação do projeto com `./gradlew compileKotlin` sem erros
- [ ] Revisão de aderência aos padrões de KDoc em toda a infraestrutura de `contract`

### Phase 6: Módulo Contract - Interfaces e Ponto de Entrada da Aplicação

- [ ] Task 26: Documentar Interfaces Web REST de Contratos (Controller, OpenAPI, Mappers e DTOs)
- [ ] Task 27: Documentar Webhooks de Provedor, Consumidores Kafka e Aplicação Principal (LocaSignApplication)

### Checkpoint: Complete Application

- [ ] Compilação global com `./gradlew compileKotlin` com sucesso
- [ ] Verificação de que 100% dos 109 arquivos Kotlin possuem KDocs padronizados
- [ ] Confirmação de que `git status` e `git diff` refletem apenas adições e substituições de documentação

## Risks and Mitigations

| Risco                                                                                     | Impacto | Mitigação                                                                                                             |
|-------------------------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------|
| Quebra de compilação ou sintaxe por edição em blocos de anotações ou imports              | Alto    | Não alterar linhas executáveis ou imports; rodar `./gradlew compileKotlin` a cada tarefa/checkpoint.                  |
| Redundância ou descrições genéricas sem utilidade real                                    | Médio   | Seguir o padrão com subdivisão clara: "O que faz" e "Responsabilidade", citando regras (R1-R10) e camadas hexagonais. |
| Inconsistência de terminologia entre módulos                                              | Baixo   | Utilizar o glossário de termos oficial definido no guia técnico (`arquitetura-tecnica.md`).                           |
| Omissão de tipos secundários em arquivos de múltiplas classes (ex.: DTOs, VOs e Entities) | Médio   | Cada tarefa possui lista exata de arquivos e tipos internos a serem revisados e documentados.                         |

## Open Questions

- Nenhuma pendência em aberto. O padrão de documentação e o escopo de 109 arquivos foram mapeados integralmente.
