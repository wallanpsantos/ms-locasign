# Checklist: simplificação do código produtivo

Plano, decisões (DS-xx), inventário, descartes, pontos mantidos e achados fora de escopo em [`plan.md`](plan.md). Marque
`[x]` ao concluir cada item. Execute na ordem; as dependências estão em cada tarefa.

Convenções de verificação (PowerShell, na raiz do repositório, com Docker ligado quando houver `*IT`):

- Testes rápidos (sem Docker): `.\gradlew.bat test --tests "*Test" --console=plain`.
- Teste específico: `.\gradlew.bat test --tests "<FQN ou padrão>" --console=plain --rerun`.
- Compilação sem warnings novos: `.\gradlew.bat compileKotlin --console=plain`.
- Arquitetura: `.\gradlew.bat test --tests "*HexagonalArchitectureTest" --console=plain`.
- Confira na saída que os testes rodaram: um `--tests` sem correspondência também passa.

Regras que valem para todas as tarefas (DS-02 e DS-03):

- Comportamento idêntico: mesmas exceções e mensagens, ordem de efeitos, transações, SQL, JSON, logs e beans.
- Quando a tarefa pede teste de caracterização, ele é escrito **antes** da mudança, passa no código atual e continua
  passando depois, sem alteração (salvo o ajuste explícito indicado na tarefa).
- Testes existentes não são alterados, exceto o fake `FakeLeaseActivation` na T16 e o tipo de `contractId` no teste
  criado na T15. Acrescentar casos novos a uma classe de teste existente é permitido, inclusive um parâmetro injetado a
  mais no construtor.
- KDoc novo segue o ADR-015 e cita regras pela localização atual (`docs/agents/...`, DS-08).

As fases 0 a 6 (T00 a T27) estão concluídas e saíram deste checklist em 2026-10-14. O detalhamento de cada tarefa, com
critérios e verificação, está no histórico do Git (fases 0 a 3: commits `c28b581` a `d434510`; fase 4: os commits
seguintes a `9ba935d`; fases 5 e 6: os commits seguintes a `270a556`). A T22 ficou só com o teste do limitador
(`plan.md`, registro da Fase 4). Não há tarefa pendente; a linha de base da T00 fica abaixo como referência.

## Linha de base (registrada na T00)

- Testes aprovados: 112 (0 falhas, 0 erros, 0 ignorados), em `.\gradlew.bat clean build --console=plain` sobre
  `d013479` (2026-10-14), com Docker Engine 29.5.3 (Rancher Desktop).
- Warnings do `compileKotlin`: nenhum.
