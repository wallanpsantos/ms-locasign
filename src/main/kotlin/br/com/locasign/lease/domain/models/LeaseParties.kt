package br.com.locasign.lease.domain.models

import br.com.locasign.shared.domain.DomainException
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email

private const val MIN_NAME_LENGTH = 3
private const val MAX_NAME_LENGTH = 120

internal fun requireValidName(raw: String, field: String): String {
    val name = raw.trim()
    if (name.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
        throw DomainException.BusinessRuleViolation(
            field,
            "O nome deve ter entre $MIN_NAME_LENGTH e $MAX_NAME_LENGTH caracteres.",
        )
    }
    return name
}

/**
 * Entidade de domínio que representa o locatário (inquilino) do imóvel.
 *
 * **Responsabilidade:**
 * - Encapsular os dados cadastrais essenciais do inquilino (nome completo, CPF e e-mail).
 * - Garantir invariantes de tamanho de nome e validade de identificadores pessoais.
 */
data class Tenant(val name: String, val cpf: Cpf, val email: Email) {
    init {
        requireValidName(name, "tenant.name")
    }
}

/**
 * Entidade de domínio que representa o signatário autorizado da imobiliária ou procurador do locador.
 *
 * **Responsabilidade:**
 * - Encapsular nome e e-mail corporativo do representante com poderes para assinar o contrato eletrônico de locação.
 * - Assegurar a integridade do nome do signatário da imobiliária.
 */
data class AgencySigner(val name: String, val email: Email) {
    init {
        requireValidName(name, "agencySigner.name")
    }
}
