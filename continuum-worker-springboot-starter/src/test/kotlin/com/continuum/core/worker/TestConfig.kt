package com.continuum.core.worker

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TestConfig {
    @Bean
    fun testDataConverter(): DataConverter {
        val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
        val km = KotlinModule.Builder().build()
        mapper.registerModule(km)
        val jacksonConverter = JacksonJsonPayloadConverter(mapper)
        return DefaultDataConverter.newDefaultInstance()
            .withPayloadConverterOverrides(jacksonConverter)
    }
}