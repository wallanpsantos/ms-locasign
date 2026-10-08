package br.com.locasign.lease.infra.persistence.repositories

import br.com.locasign.lease.infra.persistence.entities.LeaseEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

/**
 * Repositório Spring Data JDBC para operações básicas de CRUD sobre a entidade [LeaseEntity].
 *
 * **Responsabilidade:**
 * - Fornecer métodos declarativos de acesso a dados (inserção, atualização, busca por ID) na tabela relacional `leases`.
 */
interface LeaseJdbcRepository : CrudRepository<LeaseEntity, UUID>
