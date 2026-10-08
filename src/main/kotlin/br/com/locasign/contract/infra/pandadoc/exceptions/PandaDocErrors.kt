package br.com.locasign.contract.infra.pandadoc.exceptions

import br.com.locasign.contract.app.ports.out.integration.ProviderException
import org.springframework.http.HttpStatus
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

private const val MAX_DETAIL_LENGTH = 200
private val EMAIL_IN_TEXT = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

/** Respostas de erro da PandaDoc podem citar destinatários: e-mails nunca vão inteiros para logs nem auditoria. */
private fun String.sanitized(): String = replace(EMAIL_IN_TEXT, "***@***").take(MAX_DETAIL_LENGTH)

/**
 * Classifica a falha HTTP da PandaDoc (guia, seção 5.7):
 * 404/409 → ainda não pronto; 429 → limite; 403 → permissão ou créditos; 401 → credenciais
 * (retentável: a correção é de configuração, não do contrato); outros 4xx → rejeitado; 5xx → indisponível.
 */
fun RestClientException.toProviderException(operation: String): ProviderException = when (this) {
	is RestClientResponseException -> {
		val detail = responseBodyAsString.sanitized()
		val message = "PandaDoc $operation falhou com HTTP ${statusCode.value()}: $detail"
		when {
			statusCode.value() == HttpStatus.NOT_FOUND.value() || statusCode.value() == HttpStatus.CONFLICT.value() ->
				ProviderException.NotReady(message, this)
			statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value() -> ProviderException.RateLimited(message, this)
			statusCode.value() == HttpStatus.FORBIDDEN.value() -> ProviderException.Forbidden(message, this)
			statusCode.value() == HttpStatus.UNAUTHORIZED.value() ->
				ProviderException.Unavailable("PandaDoc $operation recusou as credenciais (HTTP 401)", this)
			statusCode.is4xxClientError -> ProviderException.Rejected(statusCode.value(), message, this)
			else -> ProviderException.Unavailable(message, this)
		}
	}
	is ResourceAccessException -> ProviderException.Unavailable("PandaDoc $operation indisponível: ${message.orEmpty().sanitized()}", this)
	else -> ProviderException.Unavailable("PandaDoc $operation falhou: ${message.orEmpty().sanitized()}", this)
}
