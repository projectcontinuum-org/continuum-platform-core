package org.projectcontinuum.core.cloud.gateway.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.projectcontinuum.core.cloud.gateway.exception.WorkbenchNotFoundException
import org.projectcontinuum.core.cloud.gateway.repository.WorkbenchInstanceRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Service responsible for proxying HTTP requests to workbench instances.
 *
 * Resolves the target workbench service endpoint from the database and forwards
 * the request, stripping the /workbench/{instanceName}/open prefix.
 */
@Service
class WorkbenchProxyService(
  private val repository: WorkbenchInstanceRepository
) {

  private val logger = LoggerFactory.getLogger(WorkbenchProxyService::class.java)

  private val httpClient: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

  // Headers that must not be forwarded hop-by-hop
  private val HOP_BY_HOP_HEADERS = setOf(
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
    "host", "content-length"
  )

  /**
   * Proxies an HTTP request to the target workbench instance.
   *
   * @param instanceName The workbench instance name
   * @param userId The user ID from the x-continuum-user-id header
   * @param request The incoming servlet request
   * @param response The outgoing servlet response
   */
  fun proxyRequest(
    instanceName: String,
    userId: String,
    request: HttpServletRequest,
    response: HttpServletResponse
  ) {
    // 1. Resolve the workbench instance and validate ownership
    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")

    if (entity.status != "RUNNING") {
      response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Workbench '$instanceName' is not running (status: ${entity.status})")
      return
    }

    // 2. Build the target URL
    val serviceEndpoint = "wb-${entity.instanceId}-svc.${entity.namespace}.svc.cluster.local:8080"
    val targetBaseUrl = "http://$serviceEndpoint"

    // Strip /workbench/{instanceName}/open from the request path
    val prefixPath = "/workbench/$instanceName/open"
    val requestUri = request.requestURI
    val remainingPath = if (requestUri.startsWith(prefixPath)) {
      requestUri.removePrefix(prefixPath)
    } else {
      requestUri
    }

    // Ensure path starts with /
    val targetPath = if (remainingPath.isEmpty() || !remainingPath.startsWith("/")) "/$remainingPath" else remainingPath
    val queryString = request.queryString?.let { "?$it" } ?: ""
    val targetUrl = "$targetBaseUrl$targetPath$queryString"

    logger.debug("Proxying {} {} -> {}", request.method, requestUri, targetUrl)

    try {
      // 3. Build the proxy request
      val proxyRequestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(targetUrl))
        .timeout(Duration.ofMinutes(5))

      // Forward headers (excluding hop-by-hop)
      val headerNames = request.headerNames
      while (headerNames.hasMoreElements()) {
        val headerName = headerNames.nextElement()
        if (headerName.lowercase() in HOP_BY_HOP_HEADERS) continue
        val values = request.getHeaders(headerName)
        while (values.hasMoreElements()) {
          proxyRequestBuilder.header(headerName, values.nextElement())
        }
      }

      // Set the correct Host header for the target
      proxyRequestBuilder.header("Host", serviceEndpoint)

      // Set the HTTP method and body
      val bodyPublisher = when (request.method.uppercase()) {
        "GET", "HEAD", "OPTIONS", "TRACE" -> HttpRequest.BodyPublishers.noBody()
        else -> {
          val body = request.inputStream.readAllBytes()
          HttpRequest.BodyPublishers.ofByteArray(body)
        }
      }
      proxyRequestBuilder.method(request.method.uppercase(), bodyPublisher)

      // 4. Execute the proxy request
      val proxyResponse = httpClient.send(
        proxyRequestBuilder.build(),
        HttpResponse.BodyHandlers.ofInputStream()
      )

      // 5. Copy response status
      response.status = proxyResponse.statusCode()

      // 6. Copy response headers (excluding hop-by-hop)
      proxyResponse.headers().map().forEach { (name, values) ->
        if (name.lowercase() !in HOP_BY_HOP_HEADERS) {
          values.forEach { value ->
            response.addHeader(name, value)
          }
        }
      }

      // 7. Copy response body
      proxyResponse.body().use { inputStream ->
        inputStream.transferTo(response.outputStream)
      }
      response.flushBuffer()

    } catch (e: IOException) {
      logger.error("Proxy IO error for workbench '{}': {}", instanceName, e.message)
      if (!response.isCommitted) {
        response.sendError(HttpStatus.BAD_GATEWAY.value(), "Failed to reach workbench instance: ${e.message}")
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      logger.error("Proxy request interrupted for workbench '{}'", instanceName)
      if (!response.isCommitted) {
        response.sendError(HttpStatus.GATEWAY_TIMEOUT.value(), "Proxy request interrupted")
      }
    } catch (e: Exception) {
      logger.error("Unexpected proxy error for workbench '{}': {}", instanceName, e.message, e)
      if (!response.isCommitted) {
        response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error: ${e.message}")
      }
    }
  }
}
