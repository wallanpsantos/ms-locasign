package br.com.locasign.shared.interfaces.web

import br.com.locasign.shared.app.DeadLetterReplayPort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Controller REST driving para gerenciamento operacional e suporte administrativo da mensageria.
 *
 * **Responsabilidade:**
 * - Expor o endpoint restrito `POST /api/v1/admin/dead-letters/{topic}/replay` para reprocessamento manual de mensagens em DLT.
 * - Delegar a verificação de autorização ao [OperatorAccessGuard] antes de disparar o reprocessamento.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Operação", description = "Reprocessamento da fila de erros")
class AdminController(
    private val deadLetters: DeadLetterReplayPort,
    private val operatorAccess: OperatorAccessGuard,
) {

    @PostMapping("/dead-letters/{topic}/replay")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reenvia as mensagens da DLT de um tópico para o tópico original")
    fun replay(@PathVariable topic: String): ReplayResponse {
        operatorAccess.requireOperator()
        return ReplayResponse(topic, deadLetters.replay(topic))
    }

    /**
     * DTO de resposta que sumariza o resultado da republicação de mensagens de uma DLT.
     *
     * **Responsabilidade:**
     * - Retornar o nome do tópico processado ([topic]) e a quantidade total de registros reenviados com sucesso ([replayed]).
     */
    data class ReplayResponse(val topic: String, val replayed: Int)
}
