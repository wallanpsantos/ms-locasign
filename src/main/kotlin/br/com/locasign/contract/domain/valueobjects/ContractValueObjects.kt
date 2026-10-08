package br.com.locasign.contract.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import kotlin.uuid.Uuid

/**
 * Identificador tipado único de um contrato de locação, encapsulando um [Uuid] v7.
 *
 * **Responsabilidade:**
 * - Garantir tipagem forte e imutabilidade na identificação de instâncias de contratos.
 * - Validar formato textual no parsing e disparar [DomainException.NotFound] quando inválido, prevenindo erros de formato na camada web.
 */
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

/**
 * Identificador do documento no provedor externo de assinatura eletrônica (PandaDoc).
 *
 * **Responsabilidade:**
 * - Prover tipagem forte para o identificador externo do documento gerenciado pelo provedor.
 * - Assegurar que o valor seja não vazio e sanitizado contra espaços em branco excedentes.
 */
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

/**
 * Ordem ordinal de assinatura do contrato pelas partes envolvidas (Regra R3).
 *
 * **Responsabilidade:**
 * - Impor a sequência estrita de coleta de assinaturas (primeiro locatário = ordem 1, depois imobiliária = ordem 2).
 * - Garantir que o valor ordinal seja estritamente positivo (maior ou igual a 1).
 */
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
