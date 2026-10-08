package br.com.locasign.shared.infra.persistence

import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** O driver do PostgreSQL não aceita `Instant` direto: instantes viajam como `OffsetDateTime` em UTC. */
fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

fun ResultSet.getInstant(column: String): Instant? =
	getObject(column, OffsetDateTime::class.java)?.toInstant()
