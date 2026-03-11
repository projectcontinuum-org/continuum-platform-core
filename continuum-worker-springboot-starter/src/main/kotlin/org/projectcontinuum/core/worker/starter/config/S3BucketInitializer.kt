package org.projectcontinuum.core.worker.starter.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import java.net.URI

/**
 * Initializes the S3 bucket in MinIO on application startup.
 *
 * This component is only activated when the storage type is configured as "minio"
 * via the property `continuum.core.api-server.storage.type`.
 *
 * It ensures that the required S3 bucket exists in MinIO, creating it if necessary.
 *
 * @property minioEndpoint The MinIO server endpoint URL
 * @property minioAccessKey The access key for MinIO authentication
 * @property minioSecretKey The secret key for MinIO authentication
 * @property bucketName The name of the S3 bucket to initialize
 * @property bucketRegion The AWS region to use for the bucket
 */
@Component
@ConditionalOnProperty(name = ["continuum.core.api-server.storage.type"], havingValue = "minio")
class S3BucketInitializer(
  @Value("\${continuum.core.api-server.storage.minio.endpoint}")
  private val minioEndpoint: String,
  @Value("\${continuum.core.api-server.storage.minio.access-key}")
  private val minioAccessKey: String,
  @Value("\${continuum.core.api-server.storage.minio.secret-key}")
  private val minioSecretKey: String,
  @Value("\${continuum.core.api-server.storage.bucket-name}")
  private val bucketName: String,
  @Value("\${continuum.core.api-server.storage.bucket-region}")
  private val bucketRegion: String
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(S3BucketInitializer::class.java)
  }

  /**
   * Initializes the S3 bucket in MinIO after the bean is constructed.
   *
   * This method creates a synchronous S3 client configured for MinIO with path-style access,
   * checks if the specified bucket exists, and creates it if it doesn't.
   *
   * The S3 client is properly closed after the operation completes.
   *
   * @throws Exception if there is an error connecting to MinIO or creating the bucket
   */
  @PostConstruct
  fun initializeBucket() {
    val s3Client = S3Client.builder()
      .endpointOverride(URI.create(minioEndpoint))
      .region(Region.of(bucketRegion))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(minioAccessKey, minioSecretKey)
        )
      )
      .forcePathStyle(true)
      .build()

    try {
      // Check if bucket exists
      s3Client.headBucket(
        HeadBucketRequest.builder()
          .bucket(bucketName)
          .build()
      )
      LOGGER.info("Bucket '$bucketName' already exists in MinIO")
    } catch (e: NoSuchBucketException) {
      // Create the bucket if it doesn't exist
      LOGGER.info("Bucket '$bucketName' does not exist. Creating it...")
      s3Client.createBucket(
        CreateBucketRequest.builder()
          .bucket(bucketName)
          .build()
      )
      LOGGER.info("Bucket '$bucketName' created successfully in MinIO")
    } catch (e: Exception) {
      LOGGER.error("Error checking/creating bucket '$bucketName': ${e.message}", e)
      throw e
    } finally {
      s3Client.close()
    }
  }
}

