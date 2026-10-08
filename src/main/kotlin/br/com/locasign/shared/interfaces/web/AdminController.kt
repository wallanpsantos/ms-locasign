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
 * Operação: investigar e reprocessar a fila de erros (DLT). Restrito a operadores: loopback, ou o
 * header `X-Admin-Token` quando `ADMIN_TOKEN` está configurado (ver [OperatorAccessGuard]).
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

	data class ReplayResponse(val topic: String, val replayed: Int)
}
