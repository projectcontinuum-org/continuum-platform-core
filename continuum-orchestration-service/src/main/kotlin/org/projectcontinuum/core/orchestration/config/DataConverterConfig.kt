package org.projectcontinuum.core.orchestration.config

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.spring.boot.TemporalOptionsCustomizer
import org.projectcontinuum.core.commons.context.ContinuumContextPropagator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class DataConverterConfig {

    @Bean
    @Primary
    fun dataConverter(): DataConverter {
        val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()

        val km = KotlinModule.Builder().build()
        mapper.registerModule(km)

        val jacksonConverter = JacksonJsonPayloadConverter(mapper)

        val dataConverter = DefaultDataConverter.newDefaultInstance()
            .withPayloadConverterOverrides(jacksonConverter)

        return dataConverter
    }

    /**
     * Registers the [ContinuumContextPropagator] with the Temporal workflow client.
     * This enables owner ID propagation from workflow to activity execution.
     */
    @Bean
    fun workflowClientOptionsCustomizer(): TemporalOptionsCustomizer<WorkflowClientOptions.Builder> {
        return TemporalOptionsCustomizer { builder ->
            builder.setContextPropagators(listOf(ContinuumContextPropagator()))
            builder
        }
    }
}
