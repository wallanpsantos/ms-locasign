package br.com.locasign.shared.interfaces.web

import br.com.locasign.shared.domain.DomainException
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Adaptador driving global para interceptação e padronização de exceções em respostas HTTP.
 *
 * **Responsabilidade:**
 * - Capturar e converter exceções de domínio ([DomainException]), concorrência otimista e falhas sistêmicas no formato padrão RFC 9457 (Problem Details).
 * - Mapear deterministicamente cada subtipo de erro de domínio via `when` exaustivo para seu respectivo código de status HTTP (404, 409, 422).
 * - Converter [PayloadTooLargeException] (corpo acima do limite do adaptador de entrada) em HTTP 413.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ResponseEntity<ProblemDetail> = when (e) {
        is DomainException.BusinessRuleViolation -> problem(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "business-rule",
            "Regra de negócio violada",
            e.message,
        ) { setProperty("field", e.field) }

        is DomainException.NotFound ->
            problem(HttpStatus.NOT_FOUND, "not-found", "Recurso não encontrado", e.message)

        is DomainException.ActiveContractExists ->
            problem(HttpStatus.CONFLICT, "active-contract-exists", "Já existe contrato ativo", e.message)

        is DomainException.ContractFinal ->
            problem(HttpStatus.CONFLICT, "contract-final", "Contrato em estado final", e.message)
    }

    @ExceptionHandler(PayloadTooLargeException::class)
    fun handlePayloadTooLarge(e: PayloadTooLargeException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONTENT_TOO_LARGE, "payload-too-large", "Corpo da requisição grande demais", e.message)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConcurrentUpdate(e: OptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        log.warn("Conflito de versão (lock otimista): {}", e.message)
        return problem(
            HttpStatus.CONFLICT,
            "concurrent-update",
            "Atualização concorrente",
            "O recurso foi alterado por outra operação. Tente novamente.",
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ProblemDetail> {
        log.error("Erro inesperado", e)
        return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal",
            "Erro interno",
            "Erro inesperado ao processar a requisição.",
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = validationProblem("Um ou mais campos são inválidos.")
        body.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map {
                mapOf(
                    "field" to it.field,
                    "message" to (it.defaultMessage ?: "inválido"),
                )
            },
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = validationProblem(
            "Corpo da requisição ilegível: verifique o JSON, os tipos dos campos e os campos obrigatórios.",
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    private fun problem(
        status: HttpStatus,
        slug: String,
        title: String,
        detail: String?,
        customize: ProblemDetail.() -> Unit = {},
    ): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(status).body(problemDetail(status, slug, title, detail).apply(customize))

    /** Problem Details (RFC 9457) com `type` `/problems/<slug>`; sem `detail`, o título também vira o detalhe. */
    private fun problemDetail(status: HttpStatus, slug: String, title: String, detail: String?): ProblemDetail {
        val body = ProblemDetail.forStatusAndDetail(status, detail ?: title)
        body.type = problemType(slug)
        body.title = title
        return body
    }

    private fun validationProblem(detail: String): ProblemDetail =
        problemDetail(HttpStatus.BAD_REQUEST, "validation", "Requisição inválida", detail)

    private fun problemType(slug: String): URI = URI.create("/problems/$slug")
}
