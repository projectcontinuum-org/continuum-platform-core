package com.continuum.core.worker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
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
  @ConditionalOnProperty(name = ["continuum.core.storage.type"], havingValue = "aws-s3")
  fun s3AsyncClientAws(
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
  @ConditionalOnProperty(name = ["continuum.core.worker.storage.type"], havingValue = "minio")
  fun s3AsyncClientMinio(
    @Value("\${continuum.core.worker.storage.type}")
    storageType: String,
    @Value("\${continuum.core.worker.storage.minio.endpoint}")
    minioEndpoint: String,
    @Value("\${continuum.core.worker.storage.minio.access-key}")
    minioAccessKey: String,
    @Value("\${continuum.core.worker.storage.minio.secret-key}")
    minioSecretKey: String
  ): S3AsyncClient {
    require(storageType == "minio") { "Storage type must be 'minio' to configure MinIO client" }
    return S3AsyncClient.builder()
      .endpointOverride(java.net.URI.create(minioEndpoint))
      .region(Region.US_EAST_1)
      .forcePathStyle(true)
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(minioAccessKey, minioSecretKey)
        )
      )
      .forcePathStyle(true)
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