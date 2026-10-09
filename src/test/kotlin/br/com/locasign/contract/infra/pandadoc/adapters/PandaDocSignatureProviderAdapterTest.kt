package br.com.locasign.contract.infra.pandadoc.adapters

import br.com.locasign.contract.app.ports.out.integration.LeaseTemplateData
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentRequest
import br.com.locasign.contract.app.ports.out.integration.ProviderDocumentStatus
import br.com.locasign.contract.app.ports.out.integration.ProviderException
import br.com.locasign.contract.app.ports.out.integration.ProviderRecipient
import br.com.locasign.contract.app.ports.out.integration.ProviderSendRequest
import br.com.locasign.contract.app.ports.out.integration.SignedDocument
import br.com.locasign.contract.domain.models.SignerRole
import br.com.locasign.contract.domain.valueobjects.ContractId
import br.com.locasign.contract.domain.valueobjects.ProviderDocumentId
import br.com.locasign.contract.infra.pandadoc.PandaDocClient
import br.com.locasign.contract.infra.pandadoc.SlidingWindowRateLimiter
import br.com.locasign.lease.domain.valueobjects.LeaseId
import br.com.locasign.shared.app.fakes.NoOpMetricsPort
import br.com.locasign.shared.domain.valueobjects.Cpf
import br.com.locasign.shared.domain.valueobjects.Email
import br.com.locasign.shared.domain.valueobjects.Money
import br.com.locasign.shared.infra.config.LocaSignProperties
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpHeaders
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.http.HttpClient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PandaDocSignatureProviderAdapterTest {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var adapter: PandaDocSignatureProviderAdapter

    private val apiKey = "test-pandadoc-api-key"
    private val templateId = "tmpl-sample-123"

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()

        val baseUrl = wireMockServer.baseUrl()
        val httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        val requestFactory = BufferingClientHttpRequestFactory(JdkClientHttpRequestFactory(httpClient))
        val restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "API-Key $apiKey")
            .build()

        val client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(PandaDocClient::class.java)

        val properties = LocaSignProperties.PandaDocProperties(
            baseUrl = baseUrl,
            apiKey = apiKey,
            templateId = templateId,
            downloadEnabled = true,
            rateLimitPerMinute = 100,
        )

        adapter = PandaDocSignatureProviderAdapter(
            client = client,
            properties = properties,
            rateLimiter = SlidingWindowRateLimiter(100),
            metrics = NoOpMetricsPort,
            sleeper = { /* No-op para não atrasar o teste */ },
        )
    }

    @AfterEach
    fun tearDown() {
        wireMockServer.stop()
    }

    private fun sampleRequest(): ProviderDocumentRequest = ProviderDocumentRequest(
        contractId = ContractId.new(),
        leaseId = LeaseId.new(),
        documentName = "Contrato de Locação - Teste",
        recipients = listOf(
            ProviderRecipient(
                role = SignerRole.TENANT,
                name = "Locatário Teste",
                email = Email.of("locatario@example.com"),
                signingOrder = 1,
            ),
        ),
        template = LeaseTemplateData(
            tenantName = "Locatário Teste",
            tenantCpf = Cpf.of("529.982.247-25"),
            propertyAddress = "Rua Teste, 100",
            rentAmount = Money.positive("3000.00"),
            startDate = LocalDate.of(2026, 11, 1),
            termMonths = 30,
        ),
    )

    @Test
    fun `criação de documento via API envia cabeçalho de autenticação e retorna ID criado`() {
        wireMockServer.stubFor(
            post(urlEqualTo("/documents"))
                .willReturn(jsonResponse("""{"id": "doc-created-999", "status": "document.uploaded"}""", 201))
        )

        val docId = adapter.createDocument(sampleRequest())

        assertEquals("doc-created-999", docId.value)
        wireMockServer.verify(
            postRequestedFor(urlEqualTo("/documents"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("API-Key $apiKey"))
        )
    }

    @Test
    fun `consulta de detalhes retorna status e papeis do provedor`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/documents/doc-created-999/details"))
                .willReturn(
                    okJson(
                        """
                        {
                            "id": "doc-created-999",
                            "status": "document.draft",
                            "recipients": []
                        }
                        """.trimIndent()
                    )
                )
        )

        val state = adapter.fetchState(ProviderDocumentId.of("doc-created-999"))

        assertEquals("doc-created-999", state.documentId.value)
        assertEquals(ProviderDocumentStatus.DRAFT, state.status)
    }

    @Test
    fun `envio de documento executa POST no endpoint correspondente`() {
        wireMockServer.stubFor(
            post(urlEqualTo("/documents/doc-created-999/send"))
                .willReturn(okJson("""{"id": "doc-created-999", "status": "document.sent"}"""))
        )

        adapter.send(
            ProviderDocumentId.of("doc-created-999"),
            ProviderSendRequest("Assunto do Teste", "Corpo da Mensagem"),
        )
    }

    @Test
    fun `cancelamento de documento altera status para voided`() {
        wireMockServer.stubFor(
            patch(urlEqualTo("/documents/doc-created-999/status"))
                .willReturn(aResponse().withStatus(200))
        )

        adapter.cancelDocument(ProviderDocumentId.of("doc-created-999"))
    }

    @Test
    fun `download protegido retorna bytes do documento assinado`() {
        val pdfContent = "PDF-MOCK-CONTENT".toByteArray()
        wireMockServer.stubFor(
            get(urlEqualTo("/documents/doc-created-999/download-protected"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfContent)
                )
        )

        val signed = adapter.downloadSigned(ProviderDocumentId.of("doc-created-999"))

        assertIs<SignedDocument.Available>(signed)
        assertEquals("PDF-MOCK-CONTENT", String(signed.bytes))
    }

    @Test
    fun `resposta 429 de rate limit aciona retentativa automática com sucesso`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/documents/doc-created-999/details"))
                .inScenario("RateLimitRetry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("Retried")
        )

        wireMockServer.stubFor(
            get(urlEqualTo("/documents/doc-created-999/details"))
                .inScenario("RateLimitRetry")
                .whenScenarioStateIs("Retried")
                .willReturn(okJson("""{"id": "doc-created-999", "status": "document.draft", "recipients": []}"""))
        )

        val state = adapter.fetchState(ProviderDocumentId.of("doc-created-999"))
        assertEquals(ProviderDocumentStatus.DRAFT, state.status)
    }

    @Test
    fun `erro 403 Forbidden é traduzido em ProviderException Forbidden`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/documents/doc-forbidden/details"))
                .willReturn(aResponse().withStatus(403))
        )

        assertFailsWith<ProviderException.Forbidden> {
            adapter.fetchState(ProviderDocumentId.of("doc-forbidden"))
        }
    }
}
