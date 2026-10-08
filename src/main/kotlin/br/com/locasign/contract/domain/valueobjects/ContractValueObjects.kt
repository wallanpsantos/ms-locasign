package br.com.locasign.contract.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import kotlin.uuid.Uuid

@JvmInline
value class ContractId(val value: Uuid) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ContractId = ContractId(Uuid.random())

        /** Identificador em formato inválido é tratado como recurso inexistente (404). */
        fun parse(raw: String): ContractId =
            ContractId(Uuid.parseOrNull(raw) ?: throw DomainException.NotFound("Contrato", raw))

        fun parseOrNull(raw: String?): ContractId? = raw?.let { Uuid.parseOrNull(it) }?.let(::ContractId)
    }
}

/** Identificador do documento no provedor de assinatura (texto não vazio). */
@JvmInline
value class ProviderDocumentId private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        fun of(raw: String): ProviderDocumentId {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                throw DomainException.BusinessRuleViolation("providerDocumentId", "Identificador do documento vazio.")
            }
            return ProviderDocumentId(trimmed)
        }
    }
}

/** Ordem de assinatura (R3): locatário = 1, imobiliária = 2. */
@JvmInline
value class SigningOrder private constructor(val value: Int) {

    companion object {
        fun of(value: Int): SigningOrder {
            if (value < 1) {
                throw DomainException.BusinessRuleViolation("signingOrder", "A ordem de assinatura deve ser positiva.")
            }
            return SigningOrder(value)
        }
    }
}
