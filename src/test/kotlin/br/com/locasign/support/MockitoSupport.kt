package br.com.locasign.support

import org.mockito.Mockito

/**
 * Utilitário para contornar checagem de não-nulidade do Kotlin ao utilizar matchers do Mockito.
 */
@Suppress("UNCHECKED_CAST")
fun <T> any(): T {
    Mockito.any<T>()
    return null as T
}
