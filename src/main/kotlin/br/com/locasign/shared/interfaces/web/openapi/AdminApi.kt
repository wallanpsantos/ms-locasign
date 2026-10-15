package br.com.locasign.shared.interfaces.web.openapi

import br.com.locasign.shared.interfaces.web.dto.ReplayResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Contrato de documentação OpenAPI 3.0 para a API REST administrativa e de suporte de mensageria.
 *
 * **Responsabilidade:**
 * - Definir anotações OpenAPI (sumários, códigos de status e respostas) para endpoints operacionais protegidos por token.
 */
@Tag(name = "Operação", description = "Reprocessamento da fila de erros")
interface AdminApi {

    @Operation(summary = "Reenvia as mensagens da DLT de um tópico para o tópico original. Requer header X-Admin-Token.")
    @ApiResponse(responseCode = "200", description = "Mensagens reenviadas")
    @ApiResponse(responseCode = "403", description = "Operação restrita a operadores (token ausente ou inválido)")
    fun replay(topic: String): ReplayResponse
}
