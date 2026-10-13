# ADR-015: convenção estruturada de documentação técnica de classes com KDoc

- **Status:** proposta
- **Relação com o guia:** complementa as diretrizes de engenharia e formaliza a convenção de KDoc descrita na seção de
  regras de código do `AGENTS.md`

## Contexto

O guia técnico original não especificava um formato padrão para documentação de tipos e classes com KDoc. Durante a
evolução do projeto, estabeleceu-se uma iniciativa de documentação técnica (registrada inicialmente como a decisão local
`ADR-KDOC-001` em `docs/tasks/plan.md` no commit `b334f09`) que cobriu 100% dos tipos de topo em `src/main/kotlin`.

Com a conclusão da iniciativa e o ciclo contínuo de planejamento de tarefas em `docs/tasks/`, a menção a `ADR-KDOC-001`
perdeu sua referência física no repositório. Para preservar a governança arquitetural e garantir que futuras classes e
refatorações sigam o mesmo padrão de manutenibilidade e clareza, essa convenção deve ser formalizada como um ADR
permanente em `docs/adr/`.

## Decisão

1. **Formato estruturado de topo:**
   Todo tipo de topo (`class`, `interface`, `object`, `enum`, `sealed class`/`interface`) em `src/main/kotlin` deve
   possuir um bloco KDoc no formato:

   ```kotlin
   /**
    * O que o tipo faz no fluxo de negócio e tecnicamente.
    *
    * **Responsabilidade:**
    * - Papel na arquitetura hexagonal.
    * - Invariantes ou regras (R1–R10, ADRs) que garante.
    */
   ```

2. **Membros com comportamento não óbvio:**
   Métodos, propriedades ou factories com comportamento específico, tratamento de exceções ou invariantes de negócio
   recebem KDoc de uma linha citando expressamente a regra correspondente (R1–R10), o ADR ou a seção do guia.

3. **Linguagem e estilo:**
    - Textos, explicações e comentários exclusivamente em português brasileiro (pt-BR).
    - Termos técnicos de código, bibliotecas e infraestrutura preservados em sua sintaxe original em inglês
      (`Spring Boot`,
      `Kafka`, `PandaDoc`, `PostgreSQL`, `value class`, etc.).
    - Tom técnico, direto e factual, sem adjetivos vazios ou linguagem promocional ("robusto", "perfeito", "sólido",
      "excelente").

4. **Origem:**
   Esta decisão formaliza o padrão adotado na iniciativa de KDoc estruturado introduzida no commit `b334f09`.

## Consequências

- Uniformidade na documentação do código-fonte, facilitando a navegação de pessoas desenvolvedoras e de agentes de IA.
- Explicitação imediata do Princípio da Responsabilidade Única (SRP) e dos limites da arquitetura hexagonal no cabeçalho
  de cada tipo.
- Qualquer criação, divisão ou movimentação de tipo deve manter a cobertura e a estrutura de KDoc exigida.
