package org.projectcontinuum.core.worker.starter

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.GlobalDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource

/**
 * Spring Boot auto-configuration class for the Continuum Worker starter.
 *
 * This configuration class is automatically loaded by Spring Boot's auto-configuration
 * mechanism when the `continuum-worker-springboot-starter` dependency is included.
 * It performs the following setup:
 *
 * ## Component Scanning
 * Enables component scanning for all classes in this package and sub-packages,
 * automatically registering beans for:
 * - [ContinuumNodeActivity] - Temporal activity for node execution
 * - [ContinuumWorkflow] - Temporal workflow for orchestrating node execution
 * - [StatusHelper] - Utility for publishing workflow status to Kafka
 * - Configuration classes (S3Config, CorsConfig, S3BucketInitializer)
 * - REST controllers (NodeRepositoryController)
 *
 * ## Temporal Data Converter Setup
 * Registers a custom Jackson-based data converter with Kotlin module support
 * to enable proper serialization/deserialization of Kotlin data classes in
 * Temporal workflows and activities.
 *
 * @author Continuum Team
 * @since 1.0.0
 * @see registerKotlinMapper
 */
@Configuration
@ComponentScan
@PropertySource("classpath:continuum-worker-springboot-starter.yaml", factory = YamlPropertySourceFactory::class)
class AutoConfigure {
  init {
    // Register Kotlin-aware Jackson mapper for Temporal serialization
    registerKotlinMapper()
  }
}

/**
 * Registers a Kotlin-aware Jackson ObjectMapper as Temporal's global data converter.
 *
 * This function configures Temporal to use Jackson with Kotlin module support for
 * serializing and deserializing workflow inputs, outputs, and activity parameters.
 * Without this configuration, Kotlin data classes may not serialize correctly.
 *
 * ## Configuration Details
 * - Creates a new Jackson ObjectMapper with default Temporal settings
 * - Registers the Kotlin module for proper Kotlin class support
 * - Creates a new JacksonJsonPayloadConverter with the configured mapper
 * - Overrides the default data converter globally for all Temporal operations
 *
 * ## Thread Safety
 * This function should only be called once during application startup, typically
 * from [AutoConfigure]'s init block.
 *
 * @see AutoConfigure
 */
fun registerKotlinMapper() {
  // Create Jackson mapper with Temporal's default configuration
  val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()

  // Build and register Kotlin module for proper Kotlin class serialization
  val km = KotlinModule.Builder().build()
  mapper.registerModule(km)

  // Create a payload converter using the Kotlin-aware mapper
  val jacksonConverter = JacksonJsonPayloadConverter(mapper)

  // Create a new data converter with the custom Jackson converter
  val dataConverter = DefaultDataConverter.newDefaultInstance()
    .withPayloadConverterOverrides(jacksonConverter)

  // Register globally for all Temporal operations
  GlobalDataConverter.register(dataConverter)
}