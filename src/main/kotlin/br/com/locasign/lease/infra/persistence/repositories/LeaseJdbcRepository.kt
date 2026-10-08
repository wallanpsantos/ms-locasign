package br.com.locasign.lease.infra.persistence.repositories

import br.com.locasign.lease.infra.persistence.entities.LeaseEntity
import org.springframework.data.repository.CrudRepository
import java.util.*

interface LeaseJdbcRepository : CrudRepository<LeaseEntity, UUID>
