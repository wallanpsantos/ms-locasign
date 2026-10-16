package br.com.locasign.contract.infra.persistence.mappers

import br.com.locasign.contract.domain.models.CancelReason
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractEntityMappersTest {

    private fun cancelledWith(reason: CancelReason?): Contract =
        restoreContract(ContractStatus.CANCELLED, cancelReason = reason)

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
            CancelReason.GenerationFailed("erro: timeout") to "GENERATION_FAILED: erro: timeout",
            CancelReason.Requested("desistência do locatário") to "REQUESTED: desistência do locatário",
            CancelReason.Requested("cliente: desistiu") to "REQUESTED: cliente: desistiu",
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
            "GENERATION_FAILED: erro: timeout" to CancelReason.GenerationFailed("erro: timeout"),
            "GENERATION_FAILED: " to CancelReason.GenerationFailed(""),
            "REQUESTED: desistência do locatário" to CancelReason.Requested("desistência do locatário"),
            "REQUESTED: cliente: desistiu" to CancelReason.Requested("cliente: desistiu"),
            "REQUESTED: GENERATION_FAILED: falso" to CancelReason.Requested("GENERATION_FAILED: falso"),
            "REQUESTED: " to CancelReason.Requested(""),
        )

        expectedReasons.forEach { (column, reason) ->
            assertEquals(reason, readColumn(column), "leitura de '$column'")
        }
        assertNull(readColumn(null))
    }

    @Test
    fun `falha de geração com detalhe em branco volta do banco sem detalhe`() {
        listOf("", "   ").forEach { blank ->
            val stored = cancelledWith(CancelReason.GenerationFailed(blank)).toEntity()

            assertEquals(CancelReason.GenerationFailed(null), stored.toDomain().cancelReason, "detalhe '$blank'")
        }
    }

    @Test
    fun `valor sem prefixo conhecido é lido como cancelamento pedido`() {
        val unprefixed = listOf("texto livre", "", "REQUESTED", "REQUESTED:sem espaço", "GENERATION_FAILED:sem espaço")

        unprefixed.forEach { column ->
            assertEquals(CancelReason.Requested(column), readColumn(column), "leitura de '$column'")
        }
    }
}
