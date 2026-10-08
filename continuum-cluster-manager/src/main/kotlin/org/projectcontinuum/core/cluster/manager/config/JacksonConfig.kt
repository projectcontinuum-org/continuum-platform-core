package org.projectcontinuum.core.cluster.manager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {

  @Bean
  fun kotlinModule(): KotlinModule {
    return KotlinModule.Builder()
      .enable(KotlinFeature.NullIsSameAsDefault)
      .build()
  }
}
