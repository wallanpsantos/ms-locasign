# ADR-011: porta `TransactionRunner` no lugar de decorator transacional

- **Status:** aceita
- **Relação com o guia:** ajusta a seção 3.6 ("decorator com `TransactionTemplate`")

## Contexto

O guia pede use cases sem Spring, com a transação aplicada por um decorator que envolve a execução
inteira do use case. Isso tem um problema: `CreateProviderDocument`, `SendContract` e
`ArchiveSignedDocument` fazem uma chamada HTTP à PandaDoc. Com um decorator de método inteiro, a
chamada externa (até 15 s de leitura) seguraria uma conexão de banco aberta dentro da transação.

## Decisão

A camada `app` define a porta `TransactionRunner` (`fun <T> run(block: () -> T): T`), implementada
em `shared/infra` com `TransactionTemplate`. Cada use case escolhe a fronteira transacional:

- Use cases sem chamada externa envolvem o corpo todo em uma transação.
- Use cases com chamada externa usam **duas transações curtas**, com a chamada HTTP entre elas.

`app` continua livre de Spring e testável com um `TransactionRunner` fake que só executa o bloco.
Chamadas aninhadas participam da transação em andamento (propagação REQUIRED).

## Consequências

- Nenhuma conexão de banco fica retida durante chamadas à PandaDoc.
- Nos use cases de duas transações, um crash entre a criação do documento e a segunda transação pode
  gerar um segundo documento no provedor quando a mensagem for reentregue. O risco é aceito no MVP;
  o `metadata.contract_id` permite identificar e anular o duplicado.
