package com.continuum.core.continuum_api_server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ContinuumApiServerApplication

fun main(args: Array<String>) {
	runApplication<ContinuumApiServerApplication>(*args)
}
