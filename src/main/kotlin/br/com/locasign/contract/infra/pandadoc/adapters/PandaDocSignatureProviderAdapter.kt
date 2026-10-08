package br.com.locasign.contract.infra.pandadoc.adapters

import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentState
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.infra.pandadoc.PandaDocClient
import br.com.locasign.contract.infra.pandadoc.SlidingWindowRateLimiter
import br.com.locasign.contract.infra.pandadoc.dto.request.SendDocumentRequest
import br.com.locasign.contract.infra.pandadoc.dto.request.StatusChangeRequest
import br.com.locasign.contract.infra.pandadoc.exceptions.toProviderException
import br.com.locasign.contract.infra.pandadoc.mappers.toPandaDoc
import br.com.locasign.contract.infra.pandadoc.mappers.toState
import br.com.locasign.shared.app.ports.Metrics
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

/**
 * Único lugar que conhece DTOs, nomes de status e endpoints da PandaDoc. Aplica limitador de taxa
 * por operação e retentativa com backoff exponencial em HTTP 429 (guia, seção 5.7).
 */
class PandaDocSignatureProviderAdapter(
	private val client: PandaDocClient,
	private val properties: LocaSignProperties.PandaDocProperties,
	private val rateLimiter: SlidingWindowRateLimiter,
	private val metrics: MetricsPort,
	private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : SignatureProviderPort {
	private val log = LoggerFactory.getLogger(javaClass)

	override fun createDocument(request: ProviderDocumentRequest): ProviderDocumentId {
		check(properties.templateId.isNotBlank()) { "PANDADOC_TEMPLATE_ID não configurado" }
		val body = request.toPandaDoc(properties)
		val response = call("create") { client.createDocument(body) }
		return ProviderDocumentId.of(response.id)
	}

	override fun fetchState(documentId: ProviderDocumentId): ProviderDocumentState =
		call("details") { client.details(documentId.value) }.toState(properties)

	override fun send(documentId: ProviderDocumentId, request: ProviderSendRequest) {
		call("send") { client.send(documentId.value, SendDocumentRequest(request.subject, request.message)) }
	}

	override fun cancelDocument(documentId: ProviderDocumentId) {
		call("status") { client.changeStatus(documentId.value, StatusChangeRequest(VOIDED_STATUS_CODE, VOID_NOTE)) }
	}

	override fun downloadSigned(documentId: ProviderDocumentId): SignedDocument {
		if (!properties.downloadEnabled) {
			return SignedDocument.Unavailable("download desligado (PANDADOC_DOWNLOAD_ENABLED=false)")
		}
		val response = try {
			call("download") { client.downloadProtected(documentId.value) }
		} catch (e: ProviderException.Unavailable) {
			// O download protegido só funciona com chave de produção; no sandbox a PandaDoc responde 401.
			if (e.cause is HttpClientErrorException.Unauthorized) {
				return SignedDocument.Unavailable("download exige chave de produção (HTTP 401)")
			}
			throw e
		}
		if (response.statusCode.value() == HttpStatus.ACCEPTED.value()) {
			throw ProviderException.NotReady("PDF assinado do documento $documentId ainda está sendo gerado")
		}
		val bytes = response.body ?: return SignedDocument.Unavailable("resposta sem corpo")
		return SignedDocument.Available(bytes)
	}

	/** Aplica o limite de taxa e traduz falhas HTTP; em 429 espera (backoff exponencial) e tenta de novo. */
	private fun <T> call(operation: String, block: () -> T): T {
		var attempt = 0
		while (true) {
			rateLimiter.acquire(operation)
			try {
				return block()
			} catch (e: RestClientException) {
				val translated = e.toProviderException(operation)
				if (translated !is ProviderException.RateLimited) throw translated
				metrics.count(Metrics.PROVIDER_RATE_LIMITED, "operation", operation)
				attempt++
				if (attempt > MAX_RATE_LIMIT_RETRIES) throw translated
				val backoff = INITIAL_BACKOFF_MS shl (attempt - 1)
				log.warn("PandaDoc {} respondeu 429; nova tentativa {} em {} ms", operation, attempt, backoff)
				sleeper(backoff)
			}
		}
	}

	private companion object {
		const val MAX_RATE_LIMIT_RETRIES = 3
		const val INITIAL_BACKOFF_MS = 2_000L
		const val VOIDED_STATUS_CODE = 11
		const val VOID_NOTE = "Documento anulado pelo LocaSign."
	}
}
