package org.projectcontinuum.core.credentials.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

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

  @Bean
  fun webConfigurer(): WebMvcConfigurer {
    return object : WebMvcConfigurer {

      override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/ui/**")
          .addResourceLocations("classpath:/static/")
      }

      override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/", "/ui/")
      }
    }
  }
}
