package br.com.locasign.contract.app.ports.out.integration

/**
 * Hierarquia de exceções de integração com o provedor de assinatura eletrônica, categorizadas por retentabilidade.
 *
 * **Responsabilidade:**
 * - Classificar as falhas de integração externa em transitórias (`NotReady`, `RateLimited`, `Unavailable`) e definitivas (`Forbidden`, `Rejected`).
 * - O `DefaultErrorHandler` do consumidor Kafka não faz essa distinção: os use cases capturam as definitivas e encerram o fluxo (`docs/agents/regras-de-codigo.md` §2).
 * - Mapear códigos de erro e indisponibilidades de API em tipos semânticos protegendo a camada de aplicação.
 */
sealed class ProviderException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /** O documento ainda não está pronto para despacho (HTTP 404/409 pré-processamento). Erro transitório retentável. */
    class NotReady(message: String, cause: Throwable? = null) : ProviderException(message, cause)

    /** Limite de taxa de requisições excedido junto à API do provedor (HTTP 429). Erro transitório retentável após recuo. */
    class RateLimited(message: String, cause: Throwable? = null) : ProviderException(message, cause)

    /** Falha transitória de rede, timeout, erro interno do provedor (HTTP 5xx) ou credencial recusada (HTTP 401). Erro retentável. */
    class Unavailable(message: String, cause: Throwable? = null) : ProviderException(message, cause)

    /** Permissão negada ou cota esgotada (HTTP 403). Falha definitiva, capturada pelos use cases. */
    class Forbidden(message: String, cause: Throwable? = null) : ProviderException(message, cause)

    /** Requisição rejeitada por validação estrutural ou violação de contrato de API (HTTP 4xx genérico). Falha permanente não retentável. */
    class Rejected(val httpStatus: Int, message: String, cause: Throwable? = null) :
        ProviderException(message, cause)
}

/**
 * Exceção lançada quando o corpo da requisição de webhook não representa um JSON estruturado válido ou compatível.
 *
 * **Responsabilidade:**
 * - Sinalizar anomalias de formatação no payload bruto do webhook sem impedir seu armazenamento na tabela de inbox para auditoria.
 */
class MalformedWebhookPayload(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
