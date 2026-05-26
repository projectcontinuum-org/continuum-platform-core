package org.projectcontinuum.core.cloud.gateway.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

  @Bean
  fun corsFilterRegistration(): FilterRegistrationBean<CorsFilter> {
    val config = CorsConfiguration().apply {
      addAllowedOriginPattern("*")
      addAllowedMethod("*")
      addAllowedHeader("*")
    }
    val source = UrlBasedCorsConfigurationSource().apply {
      registerCorsConfiguration("/**", config)
    }
    return FilterRegistrationBean(CorsFilter(source)).apply {
      order = Ordered.HIGHEST_PRECEDENCE
    }
  }
}
