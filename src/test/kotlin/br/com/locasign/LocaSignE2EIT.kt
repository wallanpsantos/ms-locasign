package br.com.locasign

import br.com.locasign.contract.interfaces.web.dto.response.ContractAcceptedResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractResponse
import br.com.locasign.lease.interfaces.web.dto.request.AgencySignerRequest
import br.com.locasign.lease.interfaces.web.dto.request.CreateLeaseRequest
import br.com.locasign.lease.interfaces.web.dto.request.PropertyRequest
import br.com.locasign.lease.interfaces.web.dto.request.TenantRequest
import br.com.locasign.lease.interfaces.web.dto.response.LeaseResponse
import br.com.locasign.support.TestcontainersSupport
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersSupport::class)
class LocaSignE2EIT {

    @LocalServerPort
    private var port: Int = 0

    private val restClient by lazy {
        RestClient.builder()
            .baseUrl("http://localhost:$port")
            .build()
    }

    companion object {
        private const val WEBHOOK_SECRET = "e2e-webhook-secret-key-32bytes-len"
        private lateinit var wireMockServer: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("locasign.pandadoc.base-url") { wireMockServer.baseUrl() }
            registry.add("locasign.pandadoc.api-key") { "test-e2e-api-key" }
            registry.add("locasign.pandadoc.template-id") { "tmpl-e2e-template" }
            registry.add("locasign.pandadoc.webhook-shared-key") { WEBHOOK_SECRET }
            registry.add("locasign.pandadoc.download-enabled") { "true" }
            registry.add("locasign.agency-signer.name") { "Imobiliária Prime E2E" }
            registry.add("locasign.agency-signer.email") { "assinaturas@imobiliaria.com" }
            registry.add("locasign.outbox.relay-interval") { "200ms" }
        }
    }

    private fun calculateHmac(secret: String, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        return HexFormat.of().formatHex(mac.doFinal(data))
    }

    private fun fetchLease(leaseId: String): LeaseResponse? =
        restClient.get().uri("/api/v1/leases/$leaseId").retrieve().body(LeaseResponse::class.java)

    private fun fetchContract(contractId: String): ContractResponse? =
        restClient.get().uri("/api/v1/contracts/$contractId").retrieve().body(ContractResponse::class.java)

    @Test
    fun `fluxo completo ponta a ponta cadastro de locacao solicitacao de contrato kafka e webhook de conclusao`() {
        val docId = "doc-e2e-" + UUID.randomUUID()

        // 1. Stubs no WireMock para a PandaDoc
        wireMockServer.stubFor(
            post(urlEqualTo("/documents"))
                .willReturn(jsonResponse("""{"id": "$docId", "status": "document.uploaded"}""", 201))
        )
        wireMockServer.stubFor(
            post(urlEqualTo("/documents/$docId/send"))
                .willReturn(okJson("""{"id": "$docId", "status": "document.sent"}"""))
        )
        wireMockServer.stubFor(
            get(urlEqualTo("/documents/$docId/download-protected"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody("PDF-ASSINADO-E2E".toByteArray())
                )
        )

        // 2. Cadastro da locação via POST /api/v1/leases
        val leaseRequest = CreateLeaseRequest(
            tenant = TenantRequest(
                name = "Juliana Teste E2E",
                cpf = "529.982.247-25",
                email = "juliana.e2e@example.com",
            ),
            agencySigner = AgencySignerRequest(
                name = "Imobiliária Prime E2E",
                email = "assinaturas@imobiliaria.com",
            ),
            property = PropertyRequest(address = "Rua Harmonia, 500, Vila Madalena"),
            rentAmount = "4200.00",
            startDate = LocalDate.now().plusDays(5),
            termMonths = 30,
        )

        val createLeaseResponse = restClient.post()
            .uri("/api/v1/leases")
            .contentType(MediaType.APPLICATION_JSON)
            .body(leaseRequest)
            .retrieve()
            .toBodilessEntity()

        assertEquals(201, createLeaseResponse.statusCode.value())
        val leaseLocation = assertNotNull(createLeaseResponse.headers.location?.path)
        val leaseId = leaseLocation.substringAfterLast("/")

        // Verifica estado inicial da locação (REGISTERED)
        val initialLease = assertNotNull(fetchLease(leaseId))
        assertEquals("REGISTERED", initialLease.status)

        // 3. Solicitação de emissão de contrato via POST /api/v1/leases/{leaseId}/contracts
        val requestContractResponse = assertNotNull(
            restClient.post()
                .uri("/api/v1/leases/$leaseId/contracts")
                .retrieve()
                .body(ContractAcceptedResponse::class.java)
        )
        val contractId = requestContractResponse.id
        assertEquals("DRAFT", requestContractResponse.status)

        // 4. Aguarda o processamento assíncrono (Outbox -> Kafka -> Orchestrator -> PandaDoc create)
        await().atMost(15, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(300)).untilAsserted {
            val contract = assertNotNull(fetchContract(contractId))
            assertEquals(docId, contract.providerDocumentId)
        }

        // 5. Envio do Webhook assinado da PandaDoc indicando document.completed
        val webhookPayload = """
        [
          {
            "event": "document_state_changed",
            "data": {
              "id": "$docId",
              "name": "Contrato E2E",
              "date_modified": "2026-10-09T14:30:00.000000Z",
              "status": "document.completed",
              "metadata": {
                "contract_id": "$contractId"
              },
              "recipients": [
                { "email": "juliana.e2e@example.com", "role": "Locatario", "has_completed": true },
                { "email": "assinaturas@imobiliaria.com", "role": "Imobiliaria", "has_completed": true }
              ]
            }
          }
        ]
        """.trimIndent()

        val signature = calculateHmac(WEBHOOK_SECRET, webhookPayload.toByteArray(Charsets.UTF_8))
        val webhookDeliveryId = "evt-e2e-" + UUID.randomUUID()

        val webhookResponse = restClient.post()
            .uri("/webhooks/pandadoc?signature=$signature")
            .header("X-PandaDoc-Webhook-Event-Id", webhookDeliveryId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(webhookPayload)
            .retrieve()
            .toBodilessEntity()

        assertEquals(200, webhookResponse.statusCode.value())

        // 6. Aguarda a efetivação das ações assíncronas pós-assinatura (R8)
        // - Contrato deve transicionar para COMPLETED
        // - Locação deve ser ativada (status ACTIVE)
        await().atMost(15, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(300)).untilAsserted {
            assertEquals("COMPLETED", fetchContract(contractId)?.status)
            assertEquals("ACTIVE", fetchLease(leaseId)?.status)
        }
    }
}
