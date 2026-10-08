package br.com.locasign.shared.infra.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
