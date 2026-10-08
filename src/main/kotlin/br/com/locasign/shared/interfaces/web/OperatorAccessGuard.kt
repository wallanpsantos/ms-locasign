package br.com.locasign.shared.interfaces.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

/**
 * Restringe as operações sensíveis (reprocessar a DLT, forçar a reconciliação) enquanto a API não
 * tem autenticação (decisão do MVP, registrada como risco no guia, seção 14).
 *
 * **Falha fechada:** sem `ADMIN_TOKEN` configurado, as operações ficam desabilitadas (403). Não se
 * confia no endereço de origem: um túnel (ngrok, cloudflared) usado para os webhooks chega à
 * aplicação vindo de loopback, e isso faria qualquer cliente da internet parecer o próprio host.
 */
@Component
class OperatorAccessGuard(
	@param:Value("\${locasign.admin.token:}") private val configuredToken: String,
) {

	/** Falha com HTTP 403 se a requisição atual não trouxer o token de operador correto. */
	fun requireOperator() {
		if (configuredToken.isBlank()) {
			throw ResponseStatusException(
				HttpStatus.FORBIDDEN,
				"Operações de operador desabilitadas: defina ADMIN_TOKEN para habilitá-las.",
			)
		}
		val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
		if (!tokenMatches(request)) {
			throw ResponseStatusException(HttpStatus.FORBIDDEN, "Operação restrita a operadores.")
		}
	}

	private fun tokenMatches(request: HttpServletRequest): Boolean {
		val provided = request.getHeader(TOKEN_HEADER) ?: return false
		// Compara os hashes: tempo constante e sem vazar o tamanho do token.
		return MessageDigest.isEqual(sha256(provided), sha256(configuredToken))
	}

	private fun sha256(value: String): ByteArray =
		MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

	companion object {
		const val TOKEN_HEADER = "X-Admin-Token"
	}
}
