package com.br.ms_locasign

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<MsLocasignApplication>().with(TestcontainersConfiguration::class).run(*args)
}
