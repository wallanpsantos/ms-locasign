package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import java.math.BigDecimal
import java.math.RoundingMode

/** Valor monetário em BRL com 2 casas decimais. Nunca é construído a partir de ponto flutuante. */
@JvmInline
value class Money private constructor(val amount: BigDecimal) {

    /** Representação decimal estável, por exemplo `2500.00`. */
    fun toPlainString(): String = amount.toPlainString()

    override fun toString(): String = toPlainString()

    companion object {
        private val PATTERN = Regex("""^\d+(\.\d{1,2})?$""")

        /** Valor estritamente positivo, como exigido para o aluguel (R2). */
        fun positive(raw: String, field: String = "rentAmount"): Money {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw DomainException.BusinessRuleViolation(
                    field,
                    "Valor inválido: use ponto decimal e no máximo 2 casas (ex.: 2500.00).",
                )
            }
            val amount = BigDecimal(trimmed).setScale(2, RoundingMode.UNNECESSARY)
            if (amount.signum() <= 0) {
                throw DomainException.BusinessRuleViolation(field, "O valor deve ser maior que zero.")
            }
            return Money(amount)
        }

        /** Reconstitui um valor já validado vindo da persistência. */
        fun fromStorage(amount: BigDecimal): Money = Money(amount.setScale(2, RoundingMode.HALF_UP))
    }
}
