package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Value object que representa um valor monetário em Real Brasileiro (BRL) com precisão decimal exata.
 *
 * **Responsabilidade:**
 * - Garantir precisão monetária de 2 casas decimais utilizando `BigDecimal`, prevenindo erros de arredondamento inerentes a tipos de ponto flutuante (`Double` / `Float`).
 * - Validar montantes estritamente positivos exigidos em aluguéis (regra R2) e manter formato decimal plano estável (`toPlainString`).
 * - Limitar a entrada a 10 dígitos inteiros e 2 decimais, o que cabe em `numeric(12,2)` (restrição técnica do banco, não regra de
 *   negócio nova) e rejeita textos gigantes em tempo constante, antes de qualquer construção de `BigDecimal`, cujo parse é
 *   quadrático no número de dígitos.
 */
@JvmInline
value class Money private constructor(val amount: BigDecimal) {

    /** Representação decimal estável, por exemplo `2500.00`. */
    fun toPlainString(): String = amount.toPlainString()

    override fun toString(): String = toPlainString()

    companion object {
        /** Até 10 dígitos inteiros (limite de `numeric(12,2)`); os quantificadores limitados falham no 11º dígito, sem varrer o texto. */
        private val PATTERN = Regex("""^\d{1,10}(\.\d{1,2})?$""")

        /** Valor estritamente positivo, como exigido para o aluguel (R2). */
        fun positive(raw: String, field: String = "rentAmount"): Money {
            val trimmed = raw.trim()
            if (!PATTERN.matches(trimmed)) {
                throw DomainException.BusinessRuleViolation(
                    field,
                    "Valor inválido: use ponto decimal, até 10 dígitos inteiros e no máximo 2 casas (ex.: 2500.00).",
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
