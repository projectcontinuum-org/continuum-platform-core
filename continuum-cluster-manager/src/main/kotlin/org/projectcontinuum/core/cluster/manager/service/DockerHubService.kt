package org.projectcontinuum.core.cluster.manager.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant

@Service
class DockerHubService(
  restTemplateBuilder: RestTemplateBuilder,
  private val workbenchProperties: WorkbenchProperties
) {

  private val logger = LoggerFactory.getLogger(DockerHubService::class.java)

  private val restTemplate: RestTemplate = restTemplateBuilder
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(10))
    .build()

  @Volatile
  private var cachedTags: CachedResult? = null

  companion object {
    private const val DOCKER_HUB_BASE = "https://hub.docker.com/v2/repositories"
    private val CACHE_TTL = Duration.ofMinutes(5)
  }

  fun getAvailableTags(): List<DockerHubTag> {
    cachedTags?.let { cached ->
      if (Duration.between(cached.fetchedAt, Instant.now()) < CACHE_TTL) {
        logger.debug("Returning cached Docker Hub tags ({} tags)", cached.tags.size)
        return cached.tags
      }
    }

    return fetchAndCacheTags()
  }

  @Synchronized
  private fun fetchAndCacheTags(): List<DockerHubTag> {
    // Double-check after acquiring lock
    cachedTags?.let { cached ->
      if (Duration.between(cached.fetchedAt, Instant.now()) < CACHE_TTL) {
        return cached.tags
      }
    }

    val repository = workbenchProperties.imageRepository
    val url = "$DOCKER_HUB_BASE/$repository/tags/?page_size=100&ordering=last_updated"

    logger.info("Fetching Docker Hub tags from: {}", url)

    try {
      val response = restTemplate.getForObject(url, DockerHubTagsResponse::class.java)
        ?: throw RuntimeException("Received null response from Docker Hub")

      val tags = response.results.map { result ->
        DockerHubTag(
          name = result.name,
          lastUpdated = result.lastUpdated,
          fullSize = result.fullSize
        )
      }

      cachedTags = CachedResult(tags = tags, fetchedAt = Instant.now())
      logger.info("Cached {} Docker Hub tags for repository '{}'", tags.size, repository)
      return tags
    } catch (ex: RestClientException) {
      logger.error("Failed to fetch Docker Hub tags for repository '{}'", repository, ex)
      // Return stale cache if available, otherwise propagate error
      cachedTags?.let { return it.tags }
      throw RuntimeException("Failed to fetch Docker Hub tags: ${ex.message}", ex)
    }
  }

  // ── DTOs for Docker Hub response ──────────────────────────────────

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class DockerHubTagsResponse(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<DockerHubTagResult>
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class DockerHubTagResult(
    val name: String,
    @JsonProperty("last_updated") val lastUpdated: String?,
    @JsonProperty("full_size") val fullSize: Long?
  )

  private data class CachedResult(
    val tags: List<DockerHubTag>,
    val fetchedAt: Instant
  )
}

data class DockerHubTag(
  val name: String,
  val lastUpdated: String?,
  val fullSize: Long?
)
