package br.com.locasign

import br.com.locasign.LocaSignE2EIT.Companion.VOID_DELAY
import br.com.locasign.contract.interfaces.web.dto.request.CancelContractRequest
import br.com.locasign.contract.interfaces.web.dto.response.ContractAcceptedResponse
import br.com.locasign.contract.interfaces.web.dto.response.ContractResponse
import br.com.locasign.lease.interfaces.web.dto.request.AgencySignerRequest
import br.com.locasign.lease.interfaces.web.dto.request.CreateLeaseRequest
import br.com.locasign.lease.interfaces.web.dto.request.PropertyRequest
import br.com.locasign.lease.interfaces.web.dto.request.TenantRequest
import br.com.locasign.lease.interfaces.web.dto.response.LeaseResponse
import br.com.locasign.support.TestWebhookSupport.calculateHmac
import br.com.locasign.support.TestcontainersSupport
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Fluxo ponta a ponta com o adapter real da PandaDoc apontado para o WireMock, em vez do fake da seção 7 do
 * AGENTS.md e docs/agents/testes.md §6 (ADR-014). As demais regras de integração vêm do perfil `test`.
 * Cobre também o cancelamento, com a anulação assíncrona do documento na PandaDoc (ADR-016).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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

        /** Atraso do WireMock na anulação: a requisição de cancelamento só passaria disso se chamasse a PandaDoc. */
        private val VOID_DELAY = 4.seconds

        /** Folga generosa para a resposta imediata do cancelamento, bem abaixo de [VOID_DELAY]. */
        private val MAX_CANCEL_RESPONSE_TIME = 3.seconds
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
        }
    }

    private fun fetchLease(leaseId: String): LeaseResponse? =
        restClient.get().uri("/api/v1/leases/$leaseId").retrieve().body(LeaseResponse::class.java)

    private fun fetchContract(contractId: String): ContractResponse? =
        restClient.get().uri("/api/v1/contracts/$contractId").retrieve().body(ContractResponse::class.java)

    private fun awaitAsserted(assertion: () -> Unit) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).untilAsserted { assertion() }
    }

    /** Cadastra uma locação via `POST /api/v1/leases` e devolve o id (o `Location` da resposta 201). */
    private fun registerLease(): String {
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
        return leaseLocation.substringAfterLast("/")
    }

    /** Solicita o contrato da locação via `POST /api/v1/leases/{leaseId}/contracts` e devolve o id (status `DRAFT`). */
    private fun requestContract(leaseId: String): String {
        val response = assertNotNull(
            restClient.post()
                .uri("/api/v1/leases/$leaseId/contracts")
                .retrieve()
                .body(ContractAcceptedResponse::class.java)
        )
        assertEquals("DRAFT", response.status)
        return response.id
    }

    /** Entrega à aplicação um webhook `document_state_changed` com HMAC válido; a aplicação responde 200 na hora (R7). */
    private fun postProviderWebhook(docId: String, contractId: String, status: String, recipientsCompleted: Boolean) {
        val webhookPayload = """
        [
          {
            "event": "document_state_changed",
            "data": {
              "id": "$docId",
              "name": "Contrato E2E",
              "date_modified": "2026-10-09T14:30:00.000000Z",
              "status": "$status",
              "metadata": {
                "contract_id": "$contractId"
              },
              "recipients": [
                { "email": "juliana.e2e@example.com", "role": "Locatario", "has_completed": $recipientsCompleted },
                { "email": "assinaturas@imobiliaria.com", "role": "Imobiliaria", "has_completed": $recipientsCompleted }
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
    }

    @Test
    fun `fluxo completo ponta a ponta com cadastro de locação, solicitação de contrato, Kafka e webhook de conclusão`() {
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
        val leaseId = registerLease()

        // Verifica estado inicial da locação (REGISTERED)
        val initialLease = assertNotNull(fetchLease(leaseId))
        assertEquals("REGISTERED", initialLease.status)

        // 3. Solicitação de emissão de contrato via POST /api/v1/leases/{leaseId}/contracts
        val contractId = requestContract(leaseId)

        // 4. Aguarda o processamento assíncrono (Outbox -> Kafka -> Orchestrator -> PandaDoc create)
        awaitAsserted {
            val contract = assertNotNull(fetchContract(contractId))
            assertEquals(docId, contract.providerDocumentId)
        }

        // 5. Envio do Webhook assinado da PandaDoc indicando document.completed
        postProviderWebhook(docId, contractId, "document.completed", recipientsCompleted = true)

        // 6. Aguarda a efetivação das ações assíncronas pós-assinatura (R8)
        // - Contrato deve transicionar para COMPLETED
        // - Locação deve ser ativada (status ACTIVE)
        awaitAsserted {
            assertEquals("COMPLETED", fetchContract(contractId)?.status)
            assertEquals("ACTIVE", fetchLease(leaseId)?.status)
        }
    }

    /**
     * ADR-016 e regra inviolável 9: o `POST /cancel` não espera a PandaDoc. O WireMock demora [VOID_DELAY] para
     * responder à anulação; a resposta HTTP do cancelamento volta bem antes, e o `PATCH` chega depois, vindo do
     * consumidor Kafka do evento `ContractCancelled`.
     */
    @Test
    fun `cancelamento responde na hora e a anulação do documento chega à PandaDoc de forma assíncrona`() {
        val docId = "doc-e2e-cancel-" + UUID.randomUUID()
        wireMockServer.stubFor(
            post(urlEqualTo("/documents"))
                .willReturn(jsonResponse("""{"id": "$docId", "status": "document.uploaded"}""", 201))
        )
        wireMockServer.stubFor(
            post(urlEqualTo("/documents/$docId/send"))
                .willReturn(okJson("""{"id": "$docId", "status": "document.sent"}"""))
        )
        wireMockServer.stubFor(
            patch(urlEqualTo("/documents/$docId/status"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(VOID_DELAY.inWholeMilliseconds.toInt()))
        )

        val contractId = requestContract(registerLease())
        awaitAsserted { assertEquals(docId, fetchContract(contractId)?.providerDocumentId) }

        // O documento fica pronto no provedor; o orquestrador o envia aos signatários e o contrato vai para SENT.
        postProviderWebhook(docId, contractId, "document.draft", recipientsCompleted = false)
        awaitAsserted { assertEquals("SENT", fetchContract(contractId)?.status) }

        val cancelResponseTime = measureTime {
            val cancelResponse = restClient.post()
                .uri("/api/v1/contracts/$contractId/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .body(CancelContractRequest("Dados do locatário precisam de correção"))
                .retrieve()
                .toBodilessEntity()
            assertEquals(202, cancelResponse.statusCode.value())
        }

        assertTrue(
            cancelResponseTime < MAX_CANCEL_RESPONSE_TIME,
            "O cancelamento levou $cancelResponseTime: a requisição esperou a PandaDoc, que demora $VOID_DELAY para anular",
        )
        awaitAsserted {
            wireMockServer.verify(
                1,
                patchRequestedFor(urlEqualTo("/documents/$docId/status"))
                    .withRequestBody(matchingJsonPath("$.status", equalTo("11"))),
            )
        }
        assertEquals("CANCELLED", fetchContract(contractId)?.status)
    }
}
