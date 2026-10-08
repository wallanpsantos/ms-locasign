package br.com.locasign.shared.infra.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuração da documentação interativa OpenAPI 3.0 e Swagger UI da aplicação.
 *
 * **Responsabilidade:**
 * - Customizar metadados globais da API REST (título, versão e descrição dos fluxos de negócio).
 * - Expor a especificação contratual dos endpoints nos ambientes de homologação e desenvolvimento local.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun locaSignOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("LocaSign API")
            .version("v1")
            .description(
                "Automatiza o ciclo de vida de contratos de locação residencial: geração, envio para " +
                        "assinatura eletrônica (PandaDoc), acompanhamento por webhooks e ações pós-assinatura.",
            ),
    )
}
