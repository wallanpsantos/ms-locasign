package br.com.locasign.contract.domain.services

import br.com.locasign.contract.domain.models.ContractStatus
import br.com.locasign.contract.domain.models.ContractStatus.CANCELLED
import br.com.locasign.contract.domain.models.ContractStatus.COMPLETED
import br.com.locasign.contract.domain.models.ContractStatus.DECLINED
import br.com.locasign.contract.domain.models.ContractStatus.DRAFT
import br.com.locasign.contract.domain.models.ContractStatus.EXPIRED
import br.com.locasign.contract.domain.models.ContractStatus.GENERATED
import br.com.locasign.contract.domain.models.ContractStatus.PARTIALLY_SIGNED
import br.com.locasign.contract.domain.models.ContractStatus.SENT
import br.com.locasign.contract.domain.models.ContractStatus.VIEWED
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContractTransitionPolicyTest {

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["COMPLETED", "DECLINED", "EXPIRED", "CANCELLED"])
    fun `estado final é imutável (R5)`(terminal: ContractStatus) {
        ContractStatus.entries.filter { it != terminal }.forEach { target ->
            assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(terminal, target))
        }
    }

    @Test
    fun `status não regride (R5)`() {
        assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(VIEWED, SENT))
        assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(PARTIALLY_SIGNED, VIEWED))
        assertIs<TransitionDecision.Reject>(ContractTransitionPolicy.decide(GENERATED, DRAFT))
    }

    @Test
    fun `mesmo status resulta em NoOp (R5)`() {
        ContractStatus.entries.forEach { status ->
            assertEquals(TransitionDecision.NoOp, ContractTransitionPolicy.decide(status, status))
        }
    }

    @Test
    fun `salto para a frente é aceito (ADR-010)`() {
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(SENT, COMPLETED))
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(DRAFT, GENERATED))
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(SENT, VIEWED))
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(VIEWED, PARTIALLY_SIGNED))
    }

    @ParameterizedTest
    @EnumSource(ContractStatus::class, names = ["DECLINED", "EXPIRED", "CANCELLED", "COMPLETED"])
    fun `qualquer estado não-final pode transicionar para estados finais aplicáveis (R5)`(target: ContractStatus) {
        assertEquals(TransitionDecision.Apply, ContractTransitionPolicy.decide(SENT, target))
    }
}
