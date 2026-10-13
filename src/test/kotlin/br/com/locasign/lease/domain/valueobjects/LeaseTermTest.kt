package br.com.locasign.lease.domain.valueobjects

import br.com.locasign.shared.domain.DomainException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LeaseTermTest {

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
        assertEquals("O prazo deve estar entre 1 e 120 meses.", ex.message)
    }
}
