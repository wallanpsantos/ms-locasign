package br.com.locasign.shared.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Configuração do LocaSign (variáveis de ambiente mapeadas em `application.yaml`, guia seção 11). */
@ConfigurationProperties(prefix = "locasign")
data class LocaSignProperties(
    val timezone: String = "America/Sao_Paulo",
    val agencySigner: AgencySignerProperties = AgencySignerProperties(),
    val contract: ContractProperties = ContractProperties(),
    val outbox: OutboxProperties = OutboxProperties(),
    val archive: ArchiveProperties = ArchiveProperties(),
    val jobs: JobsProperties = JobsProperties(),
    val pandadoc: PandaDocProperties = PandaDocProperties(),
) {
    data class AgencySignerProperties(val name: String? = null, val email: String? = null)

    data class ContractProperties(
        /** R4: prazo para assinatura. */
        val signatureDeadlineDays: Long = 7,
        /** R4: lembrete no 3º dia. */
        val reminderAfterDays: Long = 3,
        /** Tempo sem atualização antes de a reconciliação consultar o provedor. */
        val reconciliationStaleAfter: Duration = Duration.ofMinutes(10),
    )

    data class OutboxProperties(
        val relayInterval: Duration = Duration.ofMillis(1500),
        val batchSize: Int = 100,
        /** Tempo máximo esperando a confirmação do broker para cada mensagem. */
        val sendTimeout: Duration = Duration.ofSeconds(10),
        /** Idade a partir da qual eventos já publicados do outbox e do inbox são removidos. */
        val retention: Duration = Duration.ofDays(30),
    )

    data class ArchiveProperties(val directory: String = "./data/signed-documents")

    data class JobsProperties(
        val reconciliation: JobProperties = JobProperties(interval = Duration.ofMinutes(5)),
        val expiration: JobProperties = JobProperties(interval = Duration.ofMinutes(15)),
        val reminder: JobProperties = JobProperties(interval = Duration.ofHours(24)),
        val housekeeping: JobProperties = JobProperties(interval = Duration.ofHours(24)),
        val batchSize: Int = 50,
    )

    data class JobProperties(
        val enabled: Boolean = true,
        val interval: Duration = Duration.ofMinutes(5),
    )

    data class PandaDocProperties(
        val baseUrl: String = "https://api.pandadoc.com/public/v1",
        val apiKey: String = "",
        val templateId: String = "",
        val webhookSharedKey: String = "",
        /** `true` só com chave de produção: o download do PDF assinado não existe no sandbox. */
        val downloadEnabled: Boolean = false,
        /** Margem abaixo do limite do sandbox (10 requisições por minuto por endpoint). */
        val rateLimitPerMinute: Int = 8,
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(15),
        /** Nomes das roles no modelo; precisam bater exatamente com o campo `role` enviado à API. */
        val tenantRole: String = "Locatario",
        val agencyRole: String = "Imobiliaria",
    )
}
