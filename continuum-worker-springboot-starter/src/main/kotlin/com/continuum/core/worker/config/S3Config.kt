package com.continuum.core.worker.config

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
import java.net.URI

/**
 * Configuration class for S3-related beans.
 *
 * This class provides S3 client and transfer manager beans configured for either
 * AWS S3 or MinIO based on the `continuum.core.worker.storage.type` property.
 *
 * @author Continuum Team
 * @since 1.0.0
 */
@Configuration
class S3Config {

  /**
   * Creates an S3 async client configured for AWS S3.
   *
   * This bean is only created when `continuum.core.storage.type` is set to "aws-s3".
   *
   * @param awsProfileName The AWS profile name to use for credentials
   * @return An [S3AsyncClient] configured for AWS S3
   */
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

  /**
   * Creates an S3 async client configured for MinIO.
   *
   * This bean is only created when `continuum.core.worker.storage.type` is set to "minio".
   *
   * @param storageType The storage type (must be "minio")
   * @param minioEndpoint The MinIO server endpoint URL
   * @param bucketRegion The AWS region to use for the bucket
   * @param minioAccessKey The access key for MinIO authentication
   * @param minioSecretKey The secret key for MinIO authentication
   * @return An [S3AsyncClient] configured for MinIO
   */
  @Bean
  @ConditionalOnProperty(name = ["continuum.core.worker.storage.type"], havingValue = "minio")
  fun s3AsyncClientMinio(
    @Value("\${continuum.core.worker.storage.type}")
    storageType: String,
    @Value("\${continuum.core.worker.storage.minio.endpoint}")
    minioEndpoint: String,
    @Value("\${continuum.core.worker.storage.bucket-region}")
    bucketRegion: String,
    @Value("\${continuum.core.worker.storage.minio.access-key}")
    minioAccessKey: String,
    @Value("\${continuum.core.worker.storage.minio.secret-key}")
    minioSecretKey: String
  ): S3AsyncClient {
    require(storageType == "minio") { "Storage type must be 'minio' to configure MinIO client" }
    return S3AsyncClient.builder()
      .endpointOverride(URI.create(minioEndpoint))
      .region(Region.of(bucketRegion))
      .forcePathStyle(true)
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(minioAccessKey, minioSecretKey)
        )
      )
      .build()
  }

  /**
   * Creates an S3 transfer manager using the provided S3 async client.
   *
   * This bean wraps the S3 async client (either AWS or MinIO) with a transfer manager
   * for efficient file uploads and downloads.
   *
   * @param s3AsyncClient The S3 async client to use for transfers
   * @return An [S3TransferManager] configured with the provided client
   */
  @Bean
  fun s3TransferManager(
    s3AsyncClient: S3AsyncClient
  ): S3TransferManager {
    return S3TransferManager.builder()
      .s3Client(s3AsyncClient)
      .build()
  }
}

