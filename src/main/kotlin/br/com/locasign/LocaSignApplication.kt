package br.com.locasign

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.*

@SpringBootApplication
@ConfigurationPropertiesScan
class LocaSignApplication

fun main(args: Array<String>) {
    // Instantes sempre em UTC (guia, seção 8.1): evita conversões ambíguas entre JVM e timestamptz.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    runApplication<LocaSignApplication>(*args)
}
