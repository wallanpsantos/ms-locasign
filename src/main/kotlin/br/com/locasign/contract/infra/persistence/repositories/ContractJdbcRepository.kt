package br.com.locasign.contract.infra.persistence.repositories

import br.com.locasign.contract.infra.persistence.entities.ContractEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

interface ContractJdbcRepository : CrudRepository<ContractEntity, UUID> {

    fun findByProviderDocumentId(providerDocumentId: String): ContractEntity?
}
