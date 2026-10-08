package br.com.locasign.shared.app

/**
 * Exceção não-retentável lançada ao identificar mensagem serializada com formato corrompido ou envelope inválido.
 *
 * **Responsabilidade:**
 * - Sinalizar aos consumidores Kafka que a mensagem é estruturalmente irrecuperável e deve ser encaminhada diretamente para a DLT (Dead Letter Topic) sem tentativas inúteis de retentativa com backoff.
 */
class UnreadableMessageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Porta de saída da camada de aplicação para reprocessamento manual de mensagens armazenadas em Dead Letter Topics (DLT).
 *
 * **Responsabilidade:**
 * - Ler mensagens em quarentena na DLT de um determinado tópico e republicá-las em seu tópico original após intervenção ou correção.
 * - Retornar a contagem exata de mensagens reprocessadas para fins de auditoria e resposta em endpoints administrativos de operador.
 */
interface DeadLetterReplayPort {
    /** Republica no tópico original as mensagens da DLT. Devolve quantas foram reenviadas. */
    fun replay(sourceTopic: String): Int
}
