package br.com.locasign.shared.interfaces.web.dto

/**
 * DTO de resposta que sumariza o resultado da republicação de mensagens de uma DLT.
 *
 * **Responsabilidade:**
 * - Retornar o nome do tópico processado ([topic]) e a quantidade total de registros reenviados com sucesso ([replayed]).
 */
data class ReplayResponse(val topic: String, val replayed: Int)
