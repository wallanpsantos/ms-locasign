package br.com.locasign.contract.infra.config

import br.com.locasign.contract.app.ports.out.integration.ProviderWebhookGateway
import br.com.locasign.contract.app.ports.out.integration.SignatureProviderPort
import br.com.locasign.contract.infra.pandadoc.PandaDocClient
import br.com.locasign.contract.infra.pandadoc.SlidingWindowRateLimiter
import br.com.locasign.contract.infra.pandadoc.adapters.PandaDocSignatureProviderAdapter
import br.com.locasign.contract.infra.pandadoc.adapters.PandaDocWebhookGateway
import br.com.locasign.shared.app.ports.MetricsPort
import br.com.locasign.shared.infra.config.LocaSignProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/**
 * Configuração de beans de infraestrutura para integração com o provedor PandaDoc.
 *
 * **Responsabilidade:**
 * - Configurar o cliente [org.springframework.web.client.RestClient] com timeouts resilientes, fábrica de buffer e cabeçalho de autenticação via API-Key.
 * - Criar e disponibilizar os beans [PandaDocClient], [SlidingWindowRateLimiter], [PandaDocSignatureProviderAdapter] e [PandaDocWebhookGateway].
 */
@Configuration(proxyBeanMethods = false)
class PandaDocConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Timeouts do guia (seção 5.7): conexão de 5 s e leitura de 15 s. A autenticação é `Authorization: API-Key {chave}`. */
    @Bean
    fun pandaDocRestClient(builder: RestClient.Builder, properties: LocaSignProperties): RestClient {
        val pandadoc = properties.pandadoc
        if (pandadoc.apiKey.isBlank()) {
            log.warn("PANDADOC_API_KEY não configurada: chamadas à PandaDoc vão falhar até que ela seja definida")
        }
        val httpClient = HttpClient.newBuilder().connectTimeout(pandadoc.connectTimeout).build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(pandadoc.readTimeout) }
        return builder
            .baseUrl(pandadoc.baseUrl)
            // Com o buffer, o corpo JSON segue com `Content-Length` em vez de `Transfer-Encoding: chunked`,
            // que alguns proxies e WAFs rejeitam com 411.
            .requestFactory(BufferingClientHttpRequestFactory(requestFactory))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "API-Key ${pandadoc.apiKey}")
            .build()
    }

    @Bean
    fun pandaDocClient(pandaDocRestClient: RestClient): PandaDocClient =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(pandaDocRestClient))
            .build()
            .createClient(PandaDocClient::class.java)

    @Bean
    fun pandaDocRateLimiter(properties: LocaSignProperties): SlidingWindowRateLimiter =
        SlidingWindowRateLimiter(properties.pandadoc.rateLimitPerMinute)

    @Bean
    fun signatureProvider(
        client: PandaDocClient,
        properties: LocaSignProperties,
        rateLimiter: SlidingWindowRateLimiter,
        metrics: MetricsPort,
    ): SignatureProviderPort = PandaDocSignatureProviderAdapter(client, properties.pandadoc, rateLimiter, metrics)

    @Bean
    fun providerWebhookGateway(mapper: JsonMapper, properties: LocaSignProperties): ProviderWebhookGateway =
        PandaDocWebhookGateway(mapper, properties.pandadoc)
}
