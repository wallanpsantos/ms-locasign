package br.com.locasign.shared.interfaces.web

import br.com.locasign.shared.app.ports.CorrelationContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * Filtro servlet driving para rastreamento distribuído e propagação de cabeçalho de correlação HTTP.
 *
 * **Responsabilidade:**
 * - Interceptar requisições HTTP e capturar ou gerar o identificador de correlação (`X-Correlation-Id`).
 * - Injetar o identificador na resposta HTTP e propagá-lo no [CorrelationContext] para consistência de logs e eventos emitidos durante a requisição.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter(private val correlation: CorrelationContext) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val provided = request.getHeader(HEADER)?.trim()
        val id = provided?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH } ?: UUID.randomUUID().toString()
        response.setHeader(HEADER, id)
        correlation.with(id) { chain.doFilter(request, response) }
    }

    companion object {
        const val HEADER = "X-Correlation-Id"
        private const val MAX_LENGTH = 100
    }
}
