package br.com.locasign.shared.app.ports

import java.time.Instant
import java.time.LocalDate

/** Relógio de negócio: instantes em UTC e a data "de hoje" no fuso da operação (America/Sao_Paulo). */
interface BusinessClock {
    fun now(): Instant

    fun today(): LocalDate
}
