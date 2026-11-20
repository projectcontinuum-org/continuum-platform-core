package org.projectcontinuum.core.api.server.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration

/**
 * Enables Spring's cache abstraction. Spring Boot backs it with Caffeine (on the classpath), configured by
 * `spring.cache.*` in `application.yml`.
 *
 * Caches:
 * - [REGISTERED_NODES] — node registry rows by node id
 *   ([org.projectcontinuum.core.api.server.repository.jdbc.NodeTreeEntryRepository.findByNodeId]).
 */
@Configuration
@EnableCaching
class CacheConfig {
  companion object {
    const val REGISTERED_NODES = "REGISTERED_NODES"
  }
}
