package br.com.locasign.shared.interfaces.web

import br.com.locasign.shared.app.DeadLetterReplayPort
import br.com.locasign.shared.interfaces.web.openapi.AdminApi
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
class AdminController(
    private val deadLetters: DeadLetterReplayPort,
    private val operatorAccess: OperatorAccessGuard,
) : AdminApi {

    @PostMapping("/dead-letters/{topic}/replay")
    @ResponseStatus(HttpStatus.OK)
    override fun replay(@PathVariable topic: String): ReplayResponse {
        operatorAccess.requireOperator()
        return ReplayResponse(topic, deadLetters.replay(topic))
    }

    companion object {
        typealias ReplayResponse = br.com.locasign.shared.interfaces.web.dto.ReplayResponse
    }
}
