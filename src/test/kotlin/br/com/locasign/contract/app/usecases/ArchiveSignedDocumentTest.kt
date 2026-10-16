package br.com.locasign.contract.app.usecases

import br.com.locasign.contract.app.fakes.InMemoryContractRepository
import br.com.locasign.contract.app.fakes.InMemorySignedDocumentStorage
import br.com.locasign.contract.app.fakes.RecordingContractEventPublisher
import br.com.locasign.contract.app.fakes.RecordingSignatureProvider
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.domain.events.SignedDocumentArchived
import br.com.locasign.contract.domain.models.ChangeSource
import br.com.locasign.contract.domain.models.Contract
import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.restoreContract
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.shared.app.ConsumerGroups
import br.com.locasign.shared.app.fakes.FixedBusinessClock
import br.com.locasign.shared.app.fakes.InMemoryProcessedMessages
import br.com.locasign.shared.app.fakes.RecordingTransactionRunner
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveSignedDocumentTest {

    private val now = Instant.parse("2026-10-09T10:00:00Z")
    private val contracts = InMemoryContractRepository()
    private val publisher = RecordingContractEventPublisher()
    private val provider = RecordingSignatureProvider()
    private val storage = InMemorySignedDocumentStorage()
    private val processed = InMemoryProcessedMessages()
    private val transactions = RecordingTransactionRunner()

    private val archiveSignedDocument = ArchiveSignedDocument(
        contracts = contracts,
        provider = provider,
        storage = storage,
        persister = ContractPersister(contracts, publisher),
        processed = processed,
        clock = FixedBusinessClock(now),
        transactions = transactions,
    )

    private val document = ProviderDocumentId.of("doc-1")
    private val pdfReady = ArchiveSignedDocumentCommand("evt-pdf", document)

    private fun contractIn(status: ContractStatus, signedDocumentRef: String? = null): Contract = restoreContract(
        status = status,
        now = now,
        providerDocumentId = document,
        signedDocumentRef = signedDocumentRef,
    ).also(contracts::save)

    private val eventMarked: Boolean
        get() = processed.isProcessed(ConsumerGroups.PROVIDER_EVENTS, "evt-pdf")

    @Test
    fun `evento já processado não baixa o PDF (R6)`() {
        contractIn(ContractStatus.COMPLETED)
        processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, "evt-pdf")

        archiveSignedDocument.execute(pdfReady)

        assertTrue(provider.downloaded.isEmpty())
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["DECLINED", "EXPIRED", "CANCELLED"])
    fun `contrato que terminou sem conclusão marca o evento e não baixa o PDF`(status: ContractStatus) {
        contractIn(status)

        archiveSignedDocument.execute(pdfReady)

        assertTrue(provider.downloaded.isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `documento sem contrato marca o evento e não baixa o PDF`() {
        archiveSignedDocument.execute(pdfReady)

        assertTrue(provider.downloaded.isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `contrato ainda não concluído falha sem marcar o evento para que o consumidor retente`() {
        val contract = contractIn(ContractStatus.SENT)

        val ex = assertFailsWith<IllegalStateException> { archiveSignedDocument.execute(pdfReady) }

        assertEquals(
            "Contrato ${contract.id} ainda não está concluído; o PDF será arquivado na próxima tentativa",
            ex.message,
        )
        assertTrue(provider.downloaded.isEmpty())
        assertFalse(eventMarked)
    }

    @Test
    fun `download indisponível arquiva só a referência do documento no provedor`() {
        val contract = contractIn(ContractStatus.COMPLETED)
        provider.signedDocument = SignedDocument.Unavailable("Sandbox sem download.")

        archiveSignedDocument.execute(pdfReady)

        assertEquals("provider:doc-1", contract.signedDocumentRef)
        assertTrue(storage.stored.isEmpty())
        val event = assertIs<SignedDocumentArchived>(publisher.published.single())
        assertEquals(SignedDocumentArchived(contract.id, "REFERENCE_ONLY", "provider:doc-1", now, event.eventId), event)
        assertTrue(eventMarked)
    }

    @Test
    fun `PDF disponível é gravado no armazenamento e a localização devolvida vai para o contrato (R8)`() {
        val contract = contractIn(ContractStatus.COMPLETED)
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        provider.signedDocument = SignedDocument.Available(pdf)

        archiveSignedDocument.execute(pdfReady)

        val location = storage.location
        assertContentEquals(pdf, storage.stored[contract.id])
        assertEquals(location, contract.signedDocumentRef)
        val event = assertIs<SignedDocumentArchived>(publisher.published.single())
        assertEquals(SignedDocumentArchived(contract.id, "FILE", location, now, event.eventId), event)
        val history = contract.pullHistory().single()
        assertEquals("PDF assinado arquivado (FILE).", history.note)
        assertEquals(ChangeSource.WEBHOOK, history.source)
        assertEquals("evt-pdf", history.sourceEventId)
    }

    @Test
    fun `o mesmo evento executado duas vezes arquiva uma única vez (R6)`() {
        contractIn(ContractStatus.COMPLETED)

        archiveSignedDocument.execute(pdfReady)
        archiveSignedDocument.execute(pdfReady)

        assertEquals(listOf(document), provider.downloaded)
        assertEquals(1, publisher.published.size)
    }

    @Test
    fun `download acontece fora de transação e antes de marcar o evento (R6)`() {
        contractIn(ContractStatus.COMPLETED)
        provider.beforeEachCall = {
            assertFalse(transactions.inTransaction, "download chamado dentro da transação")
            assertFalse(eventMarked, "evento marcado antes do download")
        }

        archiveSignedDocument.execute(pdfReady)

        assertTrue(eventMarked)
    }

    @Test
    fun `evento marcado por outro consumidor durante o download não arquiva de novo (R6)`() {
        val contract = contractIn(ContractStatus.COMPLETED)
        provider.beforeEachCall = { processed.markProcessed(ConsumerGroups.PROVIDER_EVENTS, "evt-pdf") }

        archiveSignedDocument.execute(pdfReady)

        assertNull(contract.signedDocumentRef)
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `contrato já arquivado por outro evento marca o evento sem gravar nem publicar de novo`() {
        val contract = contractIn(ContractStatus.COMPLETED, signedDocumentRef = "provider:doc-0")

        archiveSignedDocument.execute(pdfReady)

        assertEquals(listOf(document), provider.downloaded)
        assertEquals("provider:doc-0", contract.signedDocumentRef)
        assertTrue(publisher.published.isEmpty())
        assertTrue(contract.pullHistory().isEmpty())
        assertTrue(eventMarked)
    }

    @Test
    fun `falha no download propaga sem marcar o evento (R6)`() {
        val contract = contractIn(ContractStatus.COMPLETED)
        provider.downloadFailure = ProviderException.Unavailable("tempo esgotado")

        assertFailsWith<ProviderException.Unavailable> { archiveSignedDocument.execute(pdfReady) }

        assertNull(contract.signedDocumentRef)
        assertFalse(eventMarked)
    }
}
