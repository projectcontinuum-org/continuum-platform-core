package org.projectcontinuum.core.cloud.gateway.config

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Spring Cloud Gateway Server MVC configuration.
//
// Uses a custom proxy handler instead of HandlerFunctions.http() to work around
// a Spring Cloud Gateway Server MVC bug where percent-encoded query parameters
// are decoded before proxying (e.g., %26 becomes &), which corrupts parameters
// containing special characters like "Aggregation & Grouping".
// See: https://github.com/spring-cloud/spring-cloud-gateway/issues/3082
//
// The custom proxy() function reads the raw URI and query string directly from the
// servlet request (which preserves percent-encoding) and forwards them as-is to
// the backend using Java's HttpClient.
//
// The dynamic workbench proxy routing (/workbench/{instanceName}/open)
// is handled by WorkbenchProxyController and WorkbenchWebSocketProxyHandler.
@Configuration
class GatewayConfig(
  @Value("\${CONTINUUM_API_SERVER_URL:http://localhost:8081}")
  private val apiServerUrl: String,

  @Value("\${CONTINUUM_CLUSTER_MANAGER_URL:http://localhost:8082}")
  private val clusterManagerUrl: String,

  @Value("\${CONTINUUM_CREDENTIALS_SERVER_URL:http://localhost:8083}")
  private val credentialsServerUrl: String,
) {

  private val logger = LoggerFactory.getLogger(GatewayConfig::class.java)

  private val httpClient: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

  private val HOP_BY_HOP_HEADERS = setOf(
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
  )

  // CORS headers from backend responses are stripped so the gateway's CorsFilter
  // is the single authority — this prevents duplicate Access-Control-Allow-* headers.
  private val CORS_HEADERS = setOf(
    "access-control-allow-origin", "access-control-allow-methods",
    "access-control-allow-headers", "access-control-allow-credentials",
    "access-control-expose-headers", "access-control-max-age", "vary"
  )

  @Bean
  fun apiServerRoute(): RouterFunction<ServerResponse> =
    route("api-server")
      .route(path("/api-server/**")) { request -> proxy(request, apiServerUrl, "/api-server") }
      .build()

  @Bean
  fun clusterManagerRoute(): RouterFunction<ServerResponse> =
    route("cluster-manager")
      .route(path("/cluster-manager/**")) { request -> proxy(request, clusterManagerUrl, "/cluster-manager") }
      .build()

  @Bean
  fun credentialsServerRoute(): RouterFunction<ServerResponse> =
    route("credentials-manager")
      .route(path("/credentials-manager/**")) { request -> proxy(request, credentialsServerUrl, "/credentials-manager") }
      .build()

  private fun proxy(request: ServerRequest, backendUrl: String, prefixToStrip: String): ServerResponse {
    val servletRequest: HttpServletRequest = request.servletRequest()

    // Strip the prefix from the raw request URI to preserve percent-encoding
    val rawUri = servletRequest.requestURI
    val remainingPath = rawUri.removePrefix(prefixToStrip).ifEmpty { "/" }
    val rawQuery = servletRequest.queryString?.let { "?$it" } ?: ""
    val targetUrl = "$backendUrl$remainingPath$rawQuery"

    logger.debug("Proxying {} {} -> {}", servletRequest.method, rawUri, targetUrl)

    // Build the proxy request
    val proxyBuilder = HttpRequest.newBuilder()
      .uri(URI(targetUrl))
      .timeout(Duration.ofMinutes(5))

    // Forward headers
    val headerNames = servletRequest.headerNames
    while (headerNames.hasMoreElements()) {
      val name = headerNames.nextElement()
      if (name.lowercase() in HOP_BY_HOP_HEADERS) continue
      val values = servletRequest.getHeaders(name)
      while (values.hasMoreElements()) {
        proxyBuilder.header(name, values.nextElement())
      }
    }

    // Set method and body
    val bodyPublisher = when (servletRequest.method.uppercase()) {
      "GET", "HEAD", "OPTIONS", "TRACE" -> HttpRequest.BodyPublishers.noBody()
      else -> HttpRequest.BodyPublishers.ofByteArray(servletRequest.inputStream.readAllBytes())
    }
    proxyBuilder.method(servletRequest.method.uppercase(), bodyPublisher)

    // Execute
    val proxyResponse = httpClient.send(proxyBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())

    // Build the response
    val responseBuilder = ServerResponse.status(proxyResponse.statusCode())
    proxyResponse.headers().map().forEach { (name, values) ->
      if (name.lowercase() !in HOP_BY_HOP_HEADERS && name.lowercase() !in CORS_HEADERS) {
        values.forEach { value -> responseBuilder.header(name, value) }
      }
    }
    return responseBuilder.body(proxyResponse.body())
  }
}
