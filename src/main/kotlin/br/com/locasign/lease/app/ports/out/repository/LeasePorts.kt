package br.com.locasign.lease.app.ports.out.repository

import br.com.locasign.lease.app.queries.LeaseDetailView
import br.com.locasign.lease.domain.models.Lease
import br.com.locasign.lease.domain.valueobjects.LeaseId

/**
 * Porta de saída da camada de aplicação para persistência e recuperação do agregado raiz [Lease].
 *
 * **Responsabilidade:**
 * - Salvar mutações do agregado de locação no banco de dados com suporte a controle de versão otimista.
 * - Carregar o agregado reconstituído a partir de seu identificador [LeaseId].
 */
interface LeaseRepositoryPort {
    fun save(lease: Lease)

    fun findById(id: LeaseId): Lease?
}

/**
 * Porta de saída da camada de aplicação para consultas otimizadas de leitura de locação.
 *
 * **Responsabilidade:**
 * - Executar projeções diretas de leitura ([findDetail]) sem instanciar o modelo transacional de domínio, retornando [LeaseDetailView].
 */
interface LeaseQueryPort {
    fun findDetail(id: LeaseId): LeaseDetailView?
}
