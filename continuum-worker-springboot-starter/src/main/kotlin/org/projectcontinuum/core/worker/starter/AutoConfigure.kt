package org.projectcontinuum.core.worker.starter

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.DataConverter
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.spring.boot.TemporalOptionsCustomizer
import org.projectcontinuum.core.commons.context.ContinuumContextPropagator
import org.springframework.context.annotation.*

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
 * - Configuration classes (S3Config, CorsConfig, S3BucketInitializer)
 * - REST controllers (NodeRepositoryController)
 * - [FeatureRegistrationPublisher] - Publishes node registrations to Kafka
 *
 * Note: Workflow orchestration (ContinuumWorkflow, InitializeActivity, StatusHelper)
 * has been moved to the `continuum-orchestration-service` module for independent scaling.
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

  /**
   *Registers a Kotlin-aware Jackson ObjectMapper as Temporal's global data converter.
   * This bean configures Temporal to use Jackson with Kotlin module support for
   * serializing and deserializing workflow inputs, outputs, and activity parameters.
   * Without this configuration, Kotlin data classes may not serialize correctly.
   * ## Configuration Details
   * - Creates a new Jackson ObjectMapper with default Temporal settings
   * - Registers the Kotlin module for proper Kotlin class support
   * - Creates a new JacksonJsonPayloadConverter with the configured mapper
   * - Overrides the default data converter globally for all Temporal operations
   * ## Thread Safety
   * This bean is thread-safe and will be managed by Spring's singleton scope.
   * It should only be initialized once during application startup.
   * @see registerKotlinMapper
   */
  @Bean
  @Primary  // this overrides Temporal's default
  fun dataConverter(): DataConverter {
    val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()

    // Build and register Kotlin module for proper Kotlin class serialization
    val km = KotlinModule.Builder().build()
    mapper.registerModule(km)

    // Create a payload converter using the Kotlin-aware mapper
    val jacksonConverter = JacksonJsonPayloadConverter(mapper)

    // Create a new data converter with the custom Jackson converter
    val dataConverter = DefaultDataConverter.newDefaultInstance()
      .withPayloadConverterOverrides(jacksonConverter)

    return dataConverter
  }

  /**
   * Registers the [ContinuumContextPropagator] with the Temporal workflow client.
   * This enables owner ID propagation from workflow to activity execution,
   * which is required for per-user credential resolution.
   */
  @Bean
  fun workflowClientOptionsCustomizer(): TemporalOptionsCustomizer<WorkflowClientOptions.Builder> {
    return TemporalOptionsCustomizer { builder ->
      builder.setContextPropagators(listOf(ContinuumContextPropagator()))
      builder
    }
  }
}