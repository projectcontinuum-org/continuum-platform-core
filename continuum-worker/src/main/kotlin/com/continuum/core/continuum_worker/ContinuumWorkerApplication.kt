package com.continuum.core.continuum_worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ContinuumWorkerApplication

fun main(args: Array<String>) {
	runApplication<ContinuumWorkerApplication>(*args)
}
