package org.projectcontinuum.core.cluster.manager.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

  companion object {
    const val WORKBENCH_AUTHORIZATION_CACHE = "workbenchAuthorization"
  }

  @Bean
  fun cacheManager(): CacheManager {
    val cacheManager = CaffeineCacheManager(WORKBENCH_AUTHORIZATION_CACHE)
    cacheManager.setCaffeine(
      Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .maximumSize(10_000)
    )
    return cacheManager
  }
}
