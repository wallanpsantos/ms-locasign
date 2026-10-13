package br.com.locasign.support

import br.com.locasign.contract.app.fakes.FixedSignatureProvider
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.convention.TestBean

/**
 * Base dos testes de integração com contexto Spring completo e PostgreSQL e Kafka reais.
 *
 * **Responsabilidade:**
 * - Aplicar o AGENTS.md §7 e docs/agents/testes.md §6: perfil `test` (`application-test.yaml`, com jobs desligados, relay do outbox a
 *   cada 200 ms, chave de webhook de teste e PDFs em diretório temporário) e o bean `signatureProvider` trocado por
 *   [FixedSignatureProvider], para que nenhum teste chame a PandaDoc.
 * - Manter uma única configuração entre as subclasses, para que o cache de contexto do Spring reaproveite os
 *   containers de [TestcontainersSupport].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestcontainersSupport::class)
abstract class IntegrationTestSupport {

    @TestBean(name = "signatureProvider", methodName = "fixedSignatureProvider")
    private lateinit var signatureProvider: SignatureProviderPort

    companion object {
        @JvmStatic
        fun fixedSignatureProvider(): SignatureProviderPort = FixedSignatureProvider()
    }
}
