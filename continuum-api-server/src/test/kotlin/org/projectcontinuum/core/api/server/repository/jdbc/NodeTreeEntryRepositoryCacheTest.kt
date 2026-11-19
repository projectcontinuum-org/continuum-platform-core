package org.projectcontinuum.core.api.server.repository.jdbc

import com.github.benmanes.caffeine.cache.Cache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.config.CacheConfig
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryEntity
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `@Cacheable` on [NodeTreeEntryRepository.findByNodeId] through the real Spring cache stack: `@EnableCaching`
 * ([CacheConfig]), Boot's cache auto-configuration and the `spring.cache.*` settings from the main `application.yml`.
 * The repository is a mock standing in for the Spring Data proxy; the caching proxy wraps it the same way.
 */
class NodeTreeEntryRepositoryCacheTest {

  private val db = mock<NodeTreeEntryRepository>()

  // The test classpath has its own application.yml, so point at the main one: these tests check the shipped settings.
  private val runner = ApplicationContextRunner()
    .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
    .withInitializer(ConfigDataApplicationContextInitializer())
    .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration::class.java))
    .withUserConfiguration(CacheConfig::class.java)
    .withBean(NodeTreeEntryRepository::class.java, { db })

  private fun row(manifest: String) = NodeTreeEntryEntity(
    id = 1, type = NodeTreeEntryType.CONTINUUM_NODE, name = "T", nodeId = "org.test.T", nodeManifest = manifest
  )

  private val v1 = row("""{"v":1}""")
  private val v2 = row("""{"v":2}""")

  private fun ApplicationContextRunner.withRepository(block: (NodeTreeEntryRepository) -> Unit) =
    run { ctx -> block(ctx.getBean(NodeTreeEntryRepository::class.java)) }

  @Test
  fun `application yml configures a bounded, short-lived Caffeine cache`() {
    runner.run { ctx ->
      val manager = ctx.getBean(CacheManager::class.java)
      assertTrue(manager is CaffeineCacheManager, "cache manager is ${manager.javaClass}")
      @Suppress("UNCHECKED_CAST")
      val policy = (manager.getCache(CacheConfig.REGISTERED_NODES)!!.nativeCache as Cache<Any, Any>).policy()
      assertEquals(10_000L, policy.eviction().get().maximum)
      assertEquals(30L, policy.expireAfterWrite().get().getExpiresAfter(TimeUnit.SECONDS))
    }
  }

  @Test
  fun `repeated lookups of a node hit the database once`() {
    whenever(db.findByNodeId("org.test.T")).thenReturn(v1)

    runner.withRepository { repository ->
      repeat(5) { assertSame(v1, repository.findByNodeId("org.test.T")) }
    }

    verify(db, times(1)).findByNodeId("org.test.T")
  }

  @Test
  fun `lookups are cached per node id`() {
    whenever(db.findByNodeId("org.test.T")).thenReturn(v1)
    whenever(db.findByNodeId("org.test.U")).thenReturn(v2)

    runner.withRepository { repository ->
      repeat(2) {
        assertSame(v1, repository.findByNodeId("org.test.T"))
        assertSame(v2, repository.findByNodeId("org.test.U"))
      }
    }

    verify(db, times(1)).findByNodeId("org.test.T")
    verify(db, times(1)).findByNodeId("org.test.U")
  }

  @Test
  fun `an unknown node is cached as missing`() {
    whenever(db.findByNodeId("org.test.Unknown")).thenReturn(null)

    runner.withRepository { repository ->
      assertNull(repository.findByNodeId("org.test.Unknown"))
      assertNull(repository.findByNodeId("org.test.Unknown"))
    }

    verify(db, times(1)).findByNodeId("org.test.Unknown")
  }

  @Test
  fun `a failing read is not cached`() {
    whenever(db.findByNodeId("org.test.T")).thenThrow(IllegalStateException("db down")).thenReturn(v1)

    runner.withRepository { repository ->
      assertThrows<IllegalStateException> { repository.findByNodeId("org.test.T") }
      assertSame(v1, repository.findByNodeId("org.test.T"))
    }
  }

  @Test
  fun `a re-registered node is seen once the entry expires`() {
    whenever(db.findByNodeId("org.test.T")).thenReturn(v1, v2)

    // CaffeineSpec durations are whole d/h/m/s units, so 1s is the shortest expiry it can express.
    runner.withPropertyValues("spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=1s").withRepository { repository ->
      assertSame(v1, repository.findByNodeId("org.test.T"))
      Thread.sleep(1_300)
      assertSame(v2, repository.findByNodeId("org.test.T"))
    }
  }

  @Test
  fun `other queries are not cached`() {
    whenever(db.findByNodeIdIn(listOf("org.test.T"))).thenReturn(listOf(v1))

    runner.withRepository { repository ->
      repeat(2) { repository.findByNodeIdIn(listOf("org.test.T")) }
    }

    verify(db, times(2)).findByNodeIdIn(listOf("org.test.T"))
  }
}
