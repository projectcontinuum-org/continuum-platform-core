package com.continuum.core.api.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.internal.crt.S3CrtAsyncClient
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager

@Component
class S3Config {

  @Bean
  fun s3Presigner(
    @Value("\${continuum.core.api-server.aws-profile-name}")
    awsProfile: String,
    @Value("\${continuum.core.api-server.cache-bucket-region}")
    awsRegion: String
  ): S3Presigner {
    return S3Presigner
      .builder()
      .credentialsProvider(ProfileCredentialsProvider.create(awsProfile))
      .region(Region.of(awsRegion))
      .build()
  }

  @Bean
  fun asyncS3Client(
    @Value("\${continuum.core.api-server.aws-profile-name}")
    awsProfileName: String,
    @Value("\${continuum.core.api-server.cache-bucket-region}")
    awsRegion: String
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
    s3AsyncClient: S3AsyncClient,
    @Value("\${continuum.core.api-server.cache-bucket-region}")
    awsRegion: String
  ): S3TransferManager {
    return S3TransferManager.builder()
      .s3Client(s3AsyncClient)
      .build()
  }

}