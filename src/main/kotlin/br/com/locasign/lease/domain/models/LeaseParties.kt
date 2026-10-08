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

/** Locatário (inquilino). */
data class Tenant(val name: String, val cpf: Cpf, val email: Email) {
    init {
        requireValidName(name, "tenant.name")
    }
}

/** Representante da imobiliária, que assina em nome do locador. */
data class AgencySigner(val name: String, val email: Email) {
    init {
        requireValidName(name, "agencySigner.name")
    }
}
