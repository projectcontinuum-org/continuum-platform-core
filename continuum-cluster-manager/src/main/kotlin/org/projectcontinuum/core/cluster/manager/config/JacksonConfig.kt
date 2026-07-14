package org.projectcontinuum.core.cluster.manager.config

import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {

  @Bean
  fun kotlinModule(): KotlinModule {
    return KotlinModule.Builder()
      .enable(KotlinFeature.NullIsSameAsDefault)
      .build()
  }
}
