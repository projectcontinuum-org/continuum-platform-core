package com.continuum.core.api.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.internal.crt.S3CrtAsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

@Configuration
class S3Config {
  @Bean
  @ConditionalOnProperty(name = ["continuum.core.api-server.storage.type"], havingValue = "aws-s3")
  fun asyncS3ClientAws(
    @Value("\${continuum.core.api-server.storage.bucket-name}")
    awsProfileName: String,
    @Value("\${continuum.core.api-server.storage.bucket-region}")
    awsRegion: String
  ): S3AsyncClient {
    return S3CrtAsyncClient.builder()
      .region(Region.of(awsRegion))
      .credentialsProvider(
        ProfileCredentialsProvider.builder()
          .profileName(awsProfileName)
          .build()
      )
      .build()
  }

  @Bean
  @ConditionalOnProperty(name = ["continuum.core.api-server.storage.type"], havingValue = "minio")
  fun s3AsyncClientMinio(
    @Value("\${continuum.core.api-server.storage.type}")
    storageType: String,
    @Value("\${continuum.core.api-server.storage.minio.endpoint}")
    minioEndpoint: String,
    @Value("\${continuum.core.api-server.storage.minio.access-key}")
    minioAccessKey: String,
    @Value("\${continuum.core.api-server.storage.minio.secret-key}")
    minioSecretKey: String,
    @Value("\${continuum.core.api-server.storage.bucket-region}")
    awsRegion: String
  ): S3AsyncClient {
    require(storageType == "minio") { "Storage type must be 'minio' to configure MinIO client" }
    return S3AsyncClient.builder()
      .endpointOverride(java.net.URI.create(minioEndpoint))
      .region(Region.of(awsRegion))
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