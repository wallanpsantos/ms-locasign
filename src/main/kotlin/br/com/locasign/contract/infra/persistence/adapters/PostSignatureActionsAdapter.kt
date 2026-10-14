package br.com.locasign.contract.infra.persistence.adapters

import br.com.locasign.contract.app.ports.out.repository.PostSignatureActionsPort
import br.com.locasign.contract.domain.valueobjects.ContractId
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import kotlin.uuid.toJavaUuid

/**
 * Adaptador de persistência que implementa [PostSignatureActionsPort] para controle transacional de efeitos colaterais pós-assinatura.
 *
 * **Responsabilidade:**
 * - Registrar a execução de ações pós-assinatura na tabela `post_signature_actions` com restrição de chave única `(contract_id, action_type)` (`ON CONFLICT DO NOTHING`).
 * - Garantir semântica de execução *at-most-once* para ativação de locação e geração de vistorias (Regras R6 e R8).
 */
@Repository
class PostSignatureActionsAdapter(private val jdbc: JdbcClient) : PostSignatureActionsPort {

    override fun registerIfAbsent(
        contractId: ContractId,
        actionType: String,
        status: String,
        detailsJson: String?,
    ): Boolean = jdbc.sql(
        """
		INSERT INTO post_signature_actions (contract_id, action_type, status, details)
		VALUES (:contractId, :actionType, :status, CAST(:details AS jsonb))
		ON CONFLICT (contract_id, action_type) DO NOTHING
		""".trimIndent(),
    )
        .param("contractId", contractId.value.toJavaUuid())
        .param("actionType", actionType)
        .param("status", status)
        .param("details", detailsJson)
        .update() == 1
}
