package br.com.locasign.lease.domain.valueobjects

import br.com.locasign.shared.domain.DomainException

/**
 * Value object que representa o prazo de vigência contratual da locação em meses.
 *
 * **Responsabilidade:**
 * - Validar limites contratuais permitidos (entre 1 e 120 meses), aplicando o padrão comercial de 30 meses para locações residenciais (regra R2).
 * - Prevenir valores nulos ou intervalos inválidos na definição temporal da vigência do contrato.
 */
@JvmInline
value class LeaseTerm private constructor(val months: Int) {

    override fun toString(): String = "$months meses"

    companion object {
        const val DEFAULT_MONTHS = 30
        private const val MIN_MONTHS = 1
        private const val MAX_MONTHS = 120

        fun of(months: Int?, field: String = "termMonths"): LeaseTerm {
            val value = months ?: DEFAULT_MONTHS
            if (value !in MIN_MONTHS..MAX_MONTHS) {
                throw DomainException.BusinessRuleViolation(
                    field,
                    "O prazo deve estar entre $MIN_MONTHS e $MAX_MONTHS meses.",
                )
            }
            return LeaseTerm(value)
        }

        /** Reconstitui um valor já validado vindo da persistência. */
        fun fromStorage(months: Int): LeaseTerm = LeaseTerm(months)
    }
}
