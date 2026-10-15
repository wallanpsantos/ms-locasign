package br.com.locasign.shared.interfaces.web

/**
 * Sinaliza que o corpo de uma requisição HTTP excede o limite aceito pelo adaptador de entrada.
 *
 * **Responsabilidade:**
 * - Atravessar o controller até o [ApiExceptionHandler], que a converte em HTTP 413 no formato Problem Details (RFC 9457,
 *   `/problems/payload-too-large`).
 * - Ficar fora de `DomainException`: o limite de tamanho é uma proteção do adaptador HTTP, não uma regra de negócio (R1–R10).
 */
class PayloadTooLargeException(val maxBytes: Int) :
    RuntimeException("O corpo da requisição excede o limite de $maxBytes bytes.")
