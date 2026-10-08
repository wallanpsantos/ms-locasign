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
 * Erros em Problem Details (RFC 9457). O mapeamento dos erros de domínio usa `when` exaustivo sobre
 * a hierarquia selada: um novo erro de domínio não compila até receber um status HTTP.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DomainException::class)
    fun handleDomain(e: DomainException): ResponseEntity<ProblemDetail> = when (e) {
        is DomainException.BusinessRuleViolation ->
            problem(HttpStatus.UNPROCESSABLE_CONTENT, "business-rule", "Regra de negócio violada", e.message)
                .also { it.body?.setProperty("field", e.field) }

        is DomainException.NotFound ->
            problem(HttpStatus.NOT_FOUND, "not-found", "Recurso não encontrado", e.message)

        is DomainException.ActiveContractExists ->
            problem(HttpStatus.CONFLICT, "active-contract-exists", "Já existe contrato ativo", e.message)

        is DomainException.ContractFinal ->
            problem(HttpStatus.CONFLICT, "contract-final", "Contrato em estado final", e.message)
    }

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
            "Erro inesperado ao processar a requisição."
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.")
        body.type = problemType("validation")
        body.title = "Requisição inválida"
        body.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map {
                mapOf(
                    "field" to it.field,
                    "message" to (it.defaultMessage ?: "inválido")
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
        val body = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Corpo da requisição ilegível: verifique o JSON, os tipos dos campos e os campos obrigatórios.",
        )
        body.type = problemType("validation")
        body.title = "Requisição inválida"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    private fun problem(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String?
    ): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(status, detail ?: title)
        body.type = problemType(type)
        body.title = title
        return ResponseEntity.status(status).body(body)
    }

    private fun problemType(slug: String): URI = URI.create("/problems/$slug")
}
