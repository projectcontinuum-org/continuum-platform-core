package org.projectcontinuum.core.cluster.manager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

/**
 * Kotlin support for the HTTP JSON mapper (Jackson 3, auto-configured by Spring Boot, which registers every
 * `JacksonModule` bean). `NullIsSameAsDefault`: an explicit `null` for a field with a Kotlin default (e.g.
 * `"resources": {"cpuRequest": null}`) takes the default, like a missing field.
 *
 * This bean used to be the Jackson 2 `KotlinModule`, which Spring Boot 4 never registers with the HTTP mapper, so
 * request bodies were read without Kotlin support: a partial `resources` object failed instead of taking the defaults.
 */
@Configuration
class JacksonConfig {

  @Bean
  fun kotlinModule(): KotlinModule {
    return KotlinModule.Builder()
      .enable(KotlinFeature.NullIsSameAsDefault)
      .build()
  }
}
