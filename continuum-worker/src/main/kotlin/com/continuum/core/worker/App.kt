package com.continuum.core.worker

import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.GlobalDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component
import java.util.*

@SpringBootApplication
class App

fun main(args: Array<String>) {
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
