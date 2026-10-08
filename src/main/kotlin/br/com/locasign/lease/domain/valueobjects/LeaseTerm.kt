package br.com.locasign.lease.domain.valueobjects

import br.com.locasign.shared.domain.DomainException

/** Prazo da locação em meses, de 1 a 120. O padrão de 30 meses é comum na locação residencial (R2). */
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
