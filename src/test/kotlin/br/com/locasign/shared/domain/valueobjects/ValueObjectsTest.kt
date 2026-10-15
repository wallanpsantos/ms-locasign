package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

class ValueObjectsTest {

    @Test
    fun `CPF válido com ou sem pontuação é aceito e normalizado (R10)`() {
        val cpf1 = Cpf.of("529.982.247-25")
        val cpf2 = Cpf.of("52998224725")

        assertEquals("52998224725", cpf1.digits)
        assertEquals("52998224725", cpf2.digits)
        assertEquals("529.982.247-25", cpf1.formatted())
    }

    @Test
    fun `CPF mascara dados sensíveis em toString e masked (R10)`() {
        val cpf = Cpf.of("529.982.247-25")
        val masked = cpf.masked()

        assertEquals("***.982.247-**", masked)
        assertEquals("***.982.247-**", cpf.toString())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "111.111.111-11",
            "00000000000",
            "123.456.789-00",
            "abc",
            "529.982.247-24", // dígito verificador incorreto
            "12345",
        ]
    )
    fun `CPF inválido lança violação de regra de negócio (R2)`(invalidCpf: String) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Cpf.of(invalidCpf)
        }
        assertEquals("CPF inválido.", ex.message)
    }

    @Test
    fun `Email normaliza para minúsculas e remove espaços`() {
        val email = Email.of("  Locatario.Silva@EXAMPLE.COM  ")
        assertEquals("locatario.silva@example.com", email.value)
        assertEquals("locatario.silva@example.com", email.toString())
    }

    @Test
    fun `Email gera versão mascarada segura para logs (R10)`() {
        val email = Email.of("locatario@example.com")
        assertEquals("l***@example.com", email.masked())
    }

    @ParameterizedTest
    @ValueSource(strings = ["invalido", "@semusuario.com", "usuario@", "usuario@.com", ""])
    fun `Email inválido lança violação de regra de negócio`(invalidEmail: String) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Email.of(invalidEmail)
        }
        assertEquals("E-mail inválido.", ex.message)
    }

    @Test
    fun `Money aceita valor monetário estritamente positivo com até duas casas decimais (R2)`() {
        val money = Money.positive("2500.50")
        assertEquals("2500.50", money.toPlainString())
        assertEquals("2500.50", money.toString())

        val integerMoney = Money.positive("3000")
        assertEquals("3000.00", integerMoney.toPlainString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00"])
    fun `Money zero lança violação de regra de negócio exigindo valor positivo (R2)`(zero: String) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Money.positive(zero)
        }
        assertEquals("O valor deve ser maior que zero.", ex.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "2500,50", "2500.555", "-50.00", "-1"])
    fun `Money com formato inválido ou com sinal lança erro de formato`(invalidMoney: String) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Money.positive(invalidMoney)
        }
        assertEquals(MONEY_FORMAT_MESSAGE, ex.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0.01", "2500.00", "9999999999", "9999999999.99"])
    fun `Money aceita do menor centavo ao maior valor que cabe em numeric(12,2) (R2)`(validMoney: String) {
        val money = Money.positive(validMoney)

        assertEquals(2, money.amount.scale())
        assertTrue(money.amount.precision() <= 12, "precisão ${money.amount.precision()} não cabe em numeric(12,2)")
    }

    @ParameterizedTest
    @ValueSource(strings = ["10000000000", "12345678901.50"])
    fun `Money com mais de 10 dígitos inteiros lança erro de formato porque estoura numeric(12,2) (R2)`(
        tooBigMoney: String,
    ) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            Money.positive(tooBigMoney)
        }

        assertEquals("rentAmount", ex.field)
        assertEquals(MONEY_FORMAT_MESSAGE, ex.message)
    }

    @Test
    fun `Money rejeita texto gigante em tempo constante sem montar o BigDecimal (L1)`() {
        val gigantic = "1".repeat(1_000_000)

        val rejection = measureTimedValue {
            assertFailsWith<DomainException.BusinessRuleViolation> { Money.positive(gigantic) }
        }

        assertEquals(MONEY_FORMAT_MESSAGE, rejection.value.message)
        assertTrue(rejection.duration < 500.milliseconds, "rejeição levou ${rejection.duration}; deveria ser O(1)")
    }

    private companion object {
        const val MONEY_FORMAT_MESSAGE =
            "Valor inválido: use ponto decimal, até 10 dígitos inteiros e no máximo 2 casas (ex.: 2500.00)."
    }
}
