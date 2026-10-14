package br.com.locasign.contract.infra.persistence.mappers

import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.Signer
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.SigningOrder
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.domain.valueobjects.Email
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractEntityMappersTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")

    private val signers = listOf(
        Signer(SignerRole.TENANT, "João da Silva", Email.of("joao@example.com"), SigningOrder.of(1)),
        Signer(SignerRole.AGENCY, "Imobiliária Central", Email.of("contato@example.com"), SigningOrder.of(2)),
    )

    private fun cancelledWith(reason: CancelReason?): Contract =
        restoreContract(ContractStatus.CANCELLED, LeaseId.new(), signers, now, cancelReason = reason)

    /** Lê a coluna `cancel_reason` como o repositório: a entidade com o valor gravado volta para o domínio. */
    private fun readColumn(column: String?): CancelReason? =
        cancelledWith(null).toEntity().copy(cancelReason = column).toDomain().cancelReason

    @Test
    fun `motivo de cancelamento é gravado na coluna cancel_reason com o prefixo do tipo`() {
        val expectedColumns = mapOf(
            CancelReason.GenerationFailed(null) to "GENERATION_FAILED",
            CancelReason.GenerationFailed("") to "GENERATION_FAILED",
            CancelReason.GenerationFailed("   ") to "GENERATION_FAILED",
            CancelReason.GenerationFailed("modelo inválido") to "GENERATION_FAILED: modelo inválido",
            CancelReason.Requested("desistência do locatário") to "REQUESTED: desistência do locatário",
            CancelReason.Requested("") to "REQUESTED: ",
        )

        expectedColumns.forEach { (reason, column) ->
            assertEquals(column, cancelledWith(reason).toEntity().cancelReason, "gravação de $reason")
        }
        assertNull(cancelledWith(null).toEntity().cancelReason)
    }

    @Test
    fun `coluna cancel_reason é lida de volta como o motivo de cancelamento`() {
        val expectedReasons = mapOf(
            "GENERATION_FAILED" to CancelReason.GenerationFailed(null),
            "GENERATION_FAILED: modelo inválido" to CancelReason.GenerationFailed("modelo inválido"),
            "GENERATION_FAILED: " to CancelReason.GenerationFailed(""),
            "REQUESTED: desistência do locatário" to CancelReason.Requested("desistência do locatário"),
            "REQUESTED: " to CancelReason.Requested(""),
        )

        expectedReasons.forEach { (column, reason) ->
            assertEquals(reason, readColumn(column), "leitura de '$column'")
        }
        assertNull(readColumn(null))
    }

    @Test
    fun `falha de geração com detalhe vazio volta do banco sem detalhe`() {
        val stored = cancelledWith(CancelReason.GenerationFailed("")).toEntity()

        assertEquals(CancelReason.GenerationFailed(null), stored.toDomain().cancelReason)
    }

    @Test
    fun `valor legado sem prefixo conhecido é lido como cancelamento pedido`() {
        assertEquals(CancelReason.Requested("texto livre"), readColumn("texto livre"))
        assertEquals(CancelReason.Requested("REQUESTED"), readColumn("REQUESTED"))
        assertEquals(CancelReason.Requested("GENERATION_FAILED:sem espaço"), readColumn("GENERATION_FAILED:sem espaço"))
    }
}
