package br.com.locasign

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.*

/**
 * Classe principal de inicialização da aplicação Spring Boot do microsserviço LocaSign.
 *
 * Configura o contexto do Spring com escaneamento automático de componentes e propriedades tipadas
 * (`@ConfigurationPropertiesScan`). No ponto de entrada (`main`), padroniza o fuso horário padrão da JVM
 * em UTC antes de subir o contexto, prevenindo distorções em conversões de datas e horas com o PostgreSQL (`timestamptz`).
 *
 * **Responsabilidade:**
 * - Ponto de entrada (entry point) e bootstrap da aplicação Spring Boot no monólito modular.
 * - Habilitar a descoberta de componentes e classes de propriedades de configuração em todos os módulos (`lease`, `contract`, `notification`, `shared`).
 * - Fixar a convenção de instantes em UTC globalmente para todo o ciclo de vida do processo da aplicação.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class LocaSignApplication

fun main(args: Array<String>) {
    // Instantes sempre em UTC (guia, seção 8.1): evita conversões ambíguas entre JVM e timestamptz.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<LocaSignApplication>(*args)
}
