package br.com.locasign.shared.infra.persistence

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Converte o [Instant] em [OffsetDateTime] UTC, para gravar em colunas `timestamptz`. */
fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

/** Lê a coluna `timestamptz` como [Instant]; `null` se o valor for nulo. */
fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()
