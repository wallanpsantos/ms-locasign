package br.com.locasign.shared.interfaces.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

/**
 * Componente de segurança driving para controle de acesso a operações sensíveis e administrativas.
 *
 * **Responsabilidade:**
 * - Validar a presença e integridade do token administrativo (`X-Admin-Token`) com comparação hash em tempo constante para mitigar timing attacks.
 * - Aplicar o princípio de segurança de falha fechada (fail-closed), rejeitando requisições com HTTP 403 Forbidden caso o token do operador não esteja configurado no ambiente.
 */
@Component
class OperatorAccessGuard(
    @Value($$"${locasign.admin.token:}") configuredToken: String,
) {
    private val configuredHash: ByteArray? = configuredToken.takeIf { it.isNotBlank() }?.let(::sha256)

    /** Falha com HTTP 403 se a requisição atual não trouxer o token de operador correto. */
    fun requireOperator() {
        val expectedHash = configuredHash ?: throw ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Operações de operador desabilitadas: defina ADMIN_TOKEN para habilitá-las.",
        )
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val provided = request.getHeader(TOKEN_HEADER) ?: throw forbidden()
        if (!MessageDigest.isEqual(sha256(provided), expectedHash)) {
            throw forbidden()
        }
    }

    private fun forbidden() = ResponseStatusException(HttpStatus.FORBIDDEN, "Operação restrita a operadores.")

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val TOKEN_HEADER = "X-Admin-Token"
    }
}
