package br.com.locasign.shared.domain

/**
 * Hierarquia selada raiz de exceções de negócio e regras de domínio da aplicação.
 *
 * **Responsabilidade:**
 * - Centralizar todos os erros esperados de domínio para mapeamento determinístico em respostas HTTP RFC 9457 (Problem Details).
 * - Garantir mensagens técnicas em português brasileiro voltadas para o consumidor da API, sem expor detalhes internos da infraestrutura.
 */
sealed class DomainException(message: String) : RuntimeException(message) {

    /**
     * Exceção disparada quando dados sintaticamente válidos violam uma regra ou invariante de negócio.
     *
     * **Responsabilidade:**
     * - Identificar o campo específico violado ([field]) e a justificativa da rejeição de negócio (mapeada para HTTP 422 Unprocessable Content).
     */
    class BusinessRuleViolation(val field: String, message: String) : DomainException(message)

    /**
     * Exceção disparada quando uma entidade ou recurso solicitado não existe no sistema.
     *
     * **Responsabilidade:**
     * - Identificar o tipo do recurso ([resource]) e seu identificador ([id]), mapeada diretamente para HTTP 404 Not Found.
     */
    class NotFound(val resource: String, val id: String) : DomainException("$resource não encontrado(a): $id")

    /**
     * Exceção disparada quando uma tentativa de criação de contrato conflita com um contrato ativo ou já concluído.
     *
     * **Responsabilidade:**
     * - Proteger a regra de negócio R1, impedindo múltiplos contratos concorrentes para uma mesma locação ([leaseId]), mapeada para HTTP 409 Conflict.
     */
    class ActiveContractExists(val leaseId: String, message: String) : DomainException(message)

    /**
     * Exceção disparada ao tentar executar uma mutação sobre um contrato em estado terminal ou imutável.
     *
     * **Responsabilidade:**
     * - Assegurar a invariante de imutabilidade dos estados finais (COMPLETED, DECLINED, EXPIRED, CANCELLED) conforme regra R5, mapeada para HTTP 409 Conflict.
     */
    class ContractFinal(val contractId: String, val status: String) :
        DomainException("O contrato $contractId está em estado final ($status) e não aceita esta operação.")
}
