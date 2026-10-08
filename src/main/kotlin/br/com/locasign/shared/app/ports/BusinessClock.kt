package br.com.locasign.shared.app.ports

import java.time.Instant
import java.time.LocalDate

/**
 * Porta de saída da aplicação para fornecimento de data e hora do sistema com consciência temporal e de fuso horário.
 *
 * **Responsabilidade:**
 * - Desacoplar a lógica de domínio e aplicação de chamadas estáticas ao relógio do sistema (`Instant.now()`, `LocalDate.now()`).
 * - Fornecer instantes canônicos em UTC ([now]) e a data corrente ([today]) no fuso horário operacional configurado (`America/Sao_Paulo`), viabilizando testes determinísticos.
 */
interface BusinessClock {
    fun now(): Instant

    fun today(): LocalDate
}
