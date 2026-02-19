package com.continuum.core.worker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.internal.crt.S3CrtAsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

@Configuration
class CorsConfig {
  @Bean
  fun corsConfigurer(): WebMvcConfigurer {
    return object : WebMvcConfigurer {
      override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
          .allowedOrigins("*")
          .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
          .allowedHeaders("*")
      }
    }
  }

  @Bean
  fun s3AsyncClient(
    @Value("\${continuum.core.worker.aws-profile-name}")
    awsProfileName: String
  ): S3AsyncClient {
    return S3CrtAsyncClient.builder()
      .region(Region.US_EAST_2)
      .credentialsProvider(
        ProfileCredentialsProvider.builder()
          .profileName(awsProfileName)
          .build()
      )
      .build()
  }

  @Bean
  fun s3TransferManager(
    s3AsyncClient: S3AsyncClient
  ): S3TransferManager {
    return S3TransferManager.builder()
      .s3Client(s3AsyncClient)
      .build()
  }
}