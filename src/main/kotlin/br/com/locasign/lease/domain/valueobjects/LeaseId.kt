package br.com.locasign.lease.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import kotlin.uuid.Uuid

/**
 * Value object que encapsula o identificador único universal (UUID) de uma locação.
 *
 * **Responsabilidade:**
 * - Garantir tipagem forte e imutável para a chave primária da locação, evitando confusão com outros UUIDs do sistema.
 * - Realizar parsing seguro a partir de strings literais ([parse]), mapeando identificadores inválidos diretamente para [DomainException.NotFound].
 */
@JvmInline
value class LeaseId(val value: Uuid) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): LeaseId = LeaseId(Uuid.random())

        /** Identificador em formato inválido é tratado como recurso inexistente (404). */
        fun parse(raw: String): LeaseId =
            LeaseId(Uuid.parseOrNull(raw) ?: throw DomainException.NotFound("Locação", raw))
    }
}
