package br.com.locasign.shared.app.ports

/**
 * Porta de saída da camada de aplicação para demarcação programática de fronteiras transacionais de banco de dados.
 *
 * **Responsabilidade:**
 * - Permitir que casos de uso controlem transações atômicas sem acoplamento com frameworks de infraestrutura como Spring (`@Transactional`), conforme ADR-011.
 * - Suportar transações de curta duração que isolam operações de banco e evitam reter conexões durante chamadas de rede externas (ex.: PandaDoc).
 * - Garantir propagação transacional (REQUIRED) e rollback automático mediante lançamento de qualquer exceção.
 */
interface TransactionRunner {
    fun <T> run(block: () -> T): T
}
