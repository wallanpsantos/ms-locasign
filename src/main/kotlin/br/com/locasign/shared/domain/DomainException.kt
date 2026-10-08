package br.com.locasign.shared.domain

/**
 * Hierarquia selada de erros de domínio. O mapeamento para HTTP usa `when` exaustivo sobre
 * esta hierarquia (ver `ApiExceptionHandler`), então um novo erro obriga a decidir o status HTTP.
 *
 * As mensagens são voltadas ao usuário da API e por isso ficam em português.
 */
sealed class DomainException(message: String) : RuntimeException(message) {

	/** Regra de negócio violada por um dado válido sintaticamente (HTTP 422). */
	class BusinessRuleViolation(val field: String, message: String) : DomainException(message)

	/** Recurso inexistente (HTTP 404). */
	class NotFound(val resource: String, val id: String) : DomainException("$resource não encontrado(a): $id")

	/** R1: já existe contrato não final (ou concluído) para a locação (HTTP 409). */
	class ActiveContractExists(val leaseId: String, message: String) : DomainException(message)

	/** Operação sobre contrato em estado final (HTTP 409). */
	class ContractFinal(val contractId: String, val status: String) :
		DomainException("O contrato $contractId está em estado final ($status) e não aceita esta operação.")
}
