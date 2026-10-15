package br.com.locasign.contract.app.fakes

import br.com.locasign.contract.app.ports.out.repository.ContractRepositoryPort
import br.com.locasign.contract.app.ports.out.repository.ReconciliationMarkerPort
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.valueobjects.ContractId
import java.time.Instant

/**
 * Repositório de contratos que conta o que os casos de uso fazem com o armazenamento em memória.
 *
 * **Responsabilidade:**
 * - Delegar tudo a [storage] sem mudar o comportamento; os testes semeiam os contratos direto em [storage].
 * - Contar as leituras do agregado por id ([findByIdCalls]), as gravações completas ([saveCalls]) e as marcas de reconciliação ([reconciliationMarks]), para provar que um caminho não carrega nem regrava o agregado (RC1 e RC2).
 */
class RecordingContractRepository(
    val storage: InMemoryContractRepository = InMemoryContractRepository(),
) : ContractRepositoryPort by storage, ReconciliationMarkerPort by storage {
    var findByIdCalls = 0
        private set
    var saveCalls = 0
        private set
    val reconciliationMarks = mutableListOf<Pair<ContractId, Instant>>()

    override fun findById(id: ContractId): Contract? {
        findByIdCalls++
        return storage.findById(id)
    }

    override fun save(contract: Contract) {
        saveCalls++
        storage.save(contract)
    }

    override fun markReconciled(contractId: ContractId, at: Instant) {
        reconciliationMarks += contractId to at
        storage.markReconciled(contractId, at)
    }
}
