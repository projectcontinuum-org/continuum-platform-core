package org.projectcontinuum.core.cluster.manager.config

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.filter.ForwardedHeaderFilter

/**
 * Honours `X-Forwarded-Prefix/Host/Proto` from the reverse proxy, so redirects (`/` → `/ui/`, Swagger UI) and the
 * OpenAPI server URL keep the external prefix. This is what `server.forward-headers-strategy=framework` would
 * register, with one exception: `GET /api/v1/workbench/authorize`.
 *
 * Traefik ForwardAuth calls `/authorize` with the original request's host in `X-Forwarded-Host`, and the endpoint
 * reads that header itself. [ForwardedHeaderFilter] removes every `X-Forwarded-*` header from the request it passes on,
 * so the endpoint must bypass it, or every workbench request would be denied.
 */
@Configuration
class ForwardedHeadersConfig {

  companion object {
    const val AUTHORIZE_PATH = "/api/v1/workbench/authorize"
  }

  @Bean
  fun forwardedHeaderFilter(): FilterRegistrationBean<ForwardedHeaderFilter> {
    val filter: ForwardedHeaderFilter = object : ForwardedHeaderFilter() {
      override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.removePrefix(request.contextPath) == AUTHORIZE_PATH
    }
    return FilterRegistrationBean(filter).apply {
      setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR)
      order = Ordered.HIGHEST_PRECEDENCE
    }
  }
}
