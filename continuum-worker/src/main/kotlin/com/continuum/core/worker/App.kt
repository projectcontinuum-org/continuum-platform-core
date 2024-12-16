package com.continuum.core.worker

import com.continuum.core.worker.model.ContinuumWorkflow
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.GlobalDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File

@SpringBootApplication
class App

fun main(args: Array<String>) {
	test()
	registerKotlinMapper()
	runApplication<App>(*args)
}

fun registerKotlinMapper() {
	val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
	val km = KotlinModule.Builder().build()
	mapper.registerModule(km)
	val jacksonConverter = JacksonJsonPayloadConverter(mapper)
	val dataConverter = DefaultDataConverter.newDefaultInstance()
		.withPayloadConverterOverrides(jacksonConverter)
	GlobalDataConverter.register(dataConverter)
}

fun test() {
	val objectMapper = ObjectMapper()
	val workflow = objectMapper.readValue(
		File("/Users/L001418/Library/Application Support/JetBrains/IntelliJIdea2023.3/scratches/workflow.cwf.json"),
		ContinuumWorkflow::class.java
	)
}
