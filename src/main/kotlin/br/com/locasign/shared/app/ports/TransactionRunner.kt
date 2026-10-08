package br.com.locasign.shared.app.ports

/**
 * Fronteira transacional exposta à camada `app`, que não conhece Spring (ADR-011).
 * Chamadas aninhadas participam da transação em andamento. Qualquer exceção desfaz a transação.
 */
interface TransactionRunner {
	fun <T> run(block: () -> T): T
}
