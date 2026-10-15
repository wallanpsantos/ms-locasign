package br.com.locasign.support

import br.com.locasign.shared.app.ports.TransactionRunner
import kotlin.test.assertFailsWith

/**
 * Executa [block] numa transação que sempre sofre rollback e devolve o que o bloco observou.
 *
 * As linhas gravadas dentro do bloco só existem para a conexão do próprio teste: o `OutboxRelay` do contexto, que roda
 * a cada 200 ms no perfil `test`, nunca as enxerga nem as publica, e nada sobra no banco compartilhado depois.
 * Use para testar o laço do relay e o gauge de atraso com um `KafkaTemplate` de teste.
 */
fun <T : Any> TransactionRunner.runAndRollback(block: () -> T): T {
    var observed: T? = null
    assertFailsWith<RollbackProbe> {
        run {
            observed = block()
            throw RollbackProbe()
        }
    }
    return checkNotNull(observed)
}

private class RollbackProbe : RuntimeException("rollback intencional do teste")
