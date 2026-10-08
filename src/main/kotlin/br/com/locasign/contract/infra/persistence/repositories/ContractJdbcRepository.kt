package br.com.locasign.contract.infra.persistence.repositories

import br.com.locasign.contract.infra.persistence.entities.ContractEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

/**
 * Repositório Spring Data JDBC para operações básicas de CRUD sobre a tabela `contracts`.
 *
 * **Responsabilidade:**
 * - Prover métodos derivados de persistência e recuperação de contratos por chave primária e por identificador do provedor externo (`provider_document_id`).
 */
interface ContractJdbcRepository : CrudRepository<ContractEntity, UUID> {

    fun findByProviderDocumentId(providerDocumentId: String): ContractEntity?
}
