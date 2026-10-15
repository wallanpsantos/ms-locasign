package br.com.locasign.shared.infra.persistence

import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** Converte o [Instant] em [OffsetDateTime] UTC, para gravar em colunas `timestamptz`. */
fun Instant.toDb(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

/** Lê a coluna `timestamptz` como [Instant]; `null` se o valor for nulo. */
fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()

/** Lê [Instant] obrigatório garantindo mensagem contextual em caso de nulidade. */
fun ResultSet.getRequiredInstant(column: String): Instant =
    checkNotNull(getInstant(column)) { "Coluna '$column' é obrigatória no banco e retornou nula." }

/** Retorna o resultado único ou `null` para consultas do [JdbcClient], eliminando [java.util.Optional]. */
fun <T> JdbcClient.MappedQuerySpec<T>.singleOrNull(): T? = optional().orElse(null)

/** Lê coluna UUID do ResultSet diretamente como [Uuid] do Kotlin. */
fun ResultSet.getKotlinUuid(column: String): Uuid? =
    getObject(column, java.util.UUID::class.java)?.toKotlinUuid()

/** Lê coluna UUID obrigatória do ResultSet diretamente como [Uuid] do Kotlin. */
fun ResultSet.getRequiredKotlinUuid(column: String): Uuid =
    checkNotNull(getKotlinUuid(column)) { "Coluna '$column' é obrigatória no banco e retornou nula." }

