package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.shared.domain.DomainException

/**
 * Value object que representa um Cadastro de Pessoas Físicas (CPF) válido da Receita Federal do Brasil.
 *
 * **Responsabilidade:**
 * - Validar a estrutura sintática, tamanho (11 dígitos numéricos) e os dígitos verificadores calculados pelo algoritmo módulo 11.
 * - Encapsular a sanitização e formatação do documento, garantindo mascaramento nativo em logs (`toString()` / `masked()`) para conformidade com privacidade de dados (LGPD).
 */
@JvmInline
value class Cpf private constructor(val digits: String) {

    fun formatted(): String =
        "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9)}"

    fun masked(): String = "***.${digits.substring(3, 6)}.${digits.substring(6, 9)}-**"

    override fun toString(): String = masked()

    companion object {
        private val INPUT = Regex("""^\d{3}\.?\d{3}\.?\d{3}-?\d{2}$""")

        fun of(raw: String, field: String = "cpf"): Cpf {
            val trimmed = raw.trim()
            if (!INPUT.matches(trimmed)) {
                throw DomainException.BusinessRuleViolation(field, "CPF inválido.")
            }
            val digits = trimmed.filter(Char::isDigit)
            if (digits.all { it == digits[0] }) {
                throw DomainException.BusinessRuleViolation(field, "CPF inválido.")
            }
            val first = checkDigit(digits.substring(0, 9), 10)
            val second = checkDigit(digits.substring(0, 9) + first, 11)
            if (digits[9].digitToInt() != first || digits[10].digitToInt() != second) {
                throw DomainException.BusinessRuleViolation(field, "CPF inválido.")
            }
            return Cpf(digits)
        }

        /** Reconstitui um valor já validado vindo da persistência. */
        fun fromStorage(digits: String): Cpf = Cpf(digits)

        private fun checkDigit(base: String, firstWeight: Int): Int {
            val sum = base.withIndex().sumOf { (index, char) -> char.digitToInt() * (firstWeight - index) }
            val remainder = (sum * 10) % 11
            return if (remainder == 10) 0 else remainder
        }
    }
}
