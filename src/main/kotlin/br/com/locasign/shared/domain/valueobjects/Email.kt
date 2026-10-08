package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.shared.domain.DomainException

/** E-mail com formato válido, normalizado em minúsculas. */
@JvmInline
value class Email private constructor(val value: String) {

	/** Forma segura para logs e notificações, por exemplo `m***@dominio.com`. */
	fun masked(): String {
		val local = value.substringBefore('@')
		val domain = value.substringAfter('@')
		return "${local.first()}***@$domain"
	}

	override fun toString(): String = value

	companion object {
		private const val MAX_LENGTH = 254
		private val PATTERN = Regex("""^[a-z0-9._%+\-]+@[a-z0-9\-]+(\.[a-z0-9\-]+)+$""")

		fun of(raw: String, field: String = "email"): Email {
			val normalized = raw.trim().lowercase()
			if (normalized.length > MAX_LENGTH || !PATTERN.matches(normalized)) {
				throw DomainException.BusinessRuleViolation(field, "E-mail inválido.")
			}
			return Email(normalized)
		}

		/** Reconstitui um valor já validado vindo da persistência. */
		fun fromStorage(value: String): Email = Email(value)
	}
}
