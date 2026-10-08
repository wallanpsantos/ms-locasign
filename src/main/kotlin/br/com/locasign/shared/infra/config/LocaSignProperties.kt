package br.com.locasign.shared.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Propriedades centralizadas de configuração da aplicação LocaSign mapeadas a partir de `locasign.*`.
 *
 * **Responsabilidade:**
 * - Centralizar e tipar todos os parâmetros operacionais e variáveis de ambiente do sistema em um objeto imutável.
 * - Disponibilizar configurações para os módulos de contrato, locação, mensageria, agendamentos e integrações externas.
 */
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
    /**
     * Parâmetros do signatário padrão da imobiliária para envio de contratos.
     *
     * **Responsabilidade:**
     * - Configurar nome e e-mail padrão utilizados quando o cadastro da locação não especifica um signatário dedicado da imobiliária.
     */
    data class AgencySignerProperties(val name: String? = null, val email: String? = null)

    /**
     * Parâmetros temporais e prazos do ciclo de vida contratual.
     *
     * **Responsabilidade:**
     * - Configurar prazos limites de assinatura (regra R4), disparos de lembretes e tolerância de estagnação para reconciliação periódica.
     */
    data class ContractProperties(
        /** R4: prazo para assinatura. */
        val signatureDeadlineDays: Long = 7,
        /** R4: lembrete no 3º dia. */
        val reminderAfterDays: Long = 3,
        /** Tempo sem atualização antes de a reconciliação consultar o provedor. */
        val reconciliationStaleAfter: Duration = Duration.ofMinutes(10),
    )

    /**
     * Parâmetros operacionais do mecanismo de Transactional Outbox e limpeza periódica.
     *
     * **Responsabilidade:**
     * - Definir intervalo de polling do relay, tamanho de lote, timeout de envio e período de retenção de histórico antes do expurgo.
     */
    data class OutboxProperties(
        val relayInterval: Duration = Duration.ofMillis(1500),
        val batchSize: Int = 100,
        /** Tempo máximo esperando a confirmação do broker para cada mensagem. */
        val sendTimeout: Duration = Duration.ofSeconds(10),
        /** Idade a partir da qual eventos já publicados do outbox e do inbox são removidos. */
        val retention: Duration = Duration.ofDays(30),
    )

    /**
     * Parâmetros do diretório de armazenamento de documentos assinados.
     *
     * **Responsabilidade:**
     * - Definir o caminho em disco onde os arquivos PDF finais são arquivados com garantia de escrita atômica.
     */
    data class ArchiveProperties(val directory: String = "./data/signed-documents")

    /**
     * Configurações conjuntas das tarefas agendadas em segundo plano (jobs).
     *
     * **Responsabilidade:**
     * - Agrupar intervalos e tamanhos de lote para reconciliação, expiração de contratos vencidos, lembretes e limpeza.
     */
    data class JobsProperties(
        val reconciliation: JobProperties = JobProperties(interval = Duration.ofMinutes(5)),
        val expiration: JobProperties = JobProperties(interval = Duration.ofMinutes(15)),
        val reminder: JobProperties = JobProperties(interval = Duration.ofHours(24)),
        val housekeeping: JobProperties = JobProperties(interval = Duration.ofHours(24)),
        val batchSize: Int = 50,
    )

    /**
     * Configuração individual para um job agendado específico.
     *
     * **Responsabilidade:**
     * - Definir estado de habilitação ([enabled]) e frequência de repetição ([interval]) de uma rotina periódica.
     */
    data class JobProperties(
        val enabled: Boolean = true,
        val interval: Duration = Duration.ofMinutes(5),
    )

    /**
     * Parâmetros de integração com a API externa e webhooks da PandaDoc.
     *
     * **Responsabilidade:**
     * - Configurar credenciais de autenticação, identificador do modelo de documento, chave compartilhada HMAC e limitador de taxa.
     */
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
