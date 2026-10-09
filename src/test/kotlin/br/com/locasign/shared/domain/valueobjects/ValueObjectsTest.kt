package br.com.locasign.shared.domain.valueobjects

import br.com.locasign.lease.domain.valueobjects.LeaseTerm
import br.com.locasign.shared.domain.DomainException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    @ValueSource(strings = [
        "111.111.111-11",
        "00000000000",
        "123.456.789-00",
        "abc",
        "529.982.247-24", // dígito verificador incorreto
        "12345",
    ])
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
        assertEquals("Valor inválido: use ponto decimal e no máximo 2 casas (ex.: 2500.00).", ex.message)
    }

    @Test
    fun `LeaseTerm padrão é 30 meses e aceita limites válidos de 1 a 120 (R2)`() {
        val defaultTerm = LeaseTerm.of(null)
        assertEquals(30, defaultTerm.months)
        assertEquals("30 meses", defaultTerm.toString())

        val minTerm = LeaseTerm.of(1)
        assertEquals(1, minTerm.months)

        val maxTerm = LeaseTerm.of(120)
        assertEquals(120, maxTerm.months)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 121, 200])
    fun `LeaseTerm fora do intervalo de 1 a 120 meses lança violação (R2)`(invalidMonths: Int) {
        val ex = assertFailsWith<DomainException.BusinessRuleViolation> {
            LeaseTerm.of(invalidMonths)
        }
        assertTrue(ex.message.orEmpty().contains("O prazo deve estar entre 1 e 120 meses."))
    }
}
