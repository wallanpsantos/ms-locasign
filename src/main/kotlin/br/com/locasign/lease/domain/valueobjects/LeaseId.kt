package br.com.locasign.lease.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import kotlin.uuid.Uuid

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
