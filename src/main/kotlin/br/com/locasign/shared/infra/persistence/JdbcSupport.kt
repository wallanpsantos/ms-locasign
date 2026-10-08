package br.com.locasign.shared.infra.persistence

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Funções de extensão para suporte e interoperabilidade com tipos temporais no driver PostgreSQL JDBC.
 *
 * **Responsabilidade:**
 * - Converter [Instant] para [OffsetDateTime] em UTC para persistência compatível com colunas `timestamptz`.
 * - Extrair [Instant] com segurança de resultados relacionais do [ResultSet].
 */
fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()
