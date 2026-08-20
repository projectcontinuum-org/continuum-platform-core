package org.projectcontinuum.core.knime.scheduler.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Local-dev convenience: auto-creates the dedicated workflow bucket against MinIO on
 * startup so a fresh docker-compose environment doesn't require a manual `mc mb`.
 * Not enabled for the aws-s3 backend — the production bucket is expected to already exist.
 */
@Component
@ConditionalOnProperty(name = ["continuum.core.knime-scheduler.storage.type"], havingValue = "minio")
class S3BucketInitializer(
  private val s3Client: S3Client,
  @Value("\${continuum.core.knime-scheduler.storage.bucket-name}")
  private val bucketName: String
) {

  private val log = LoggerFactory.getLogger(S3BucketInitializer::class.java)

  @PostConstruct
  fun ensureBucketExists() {
    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
    } catch (e: S3Exception) {
      if (e.statusCode() != 404) {
        throw e
      }
      log.info("Bucket '{}' not found — creating it", bucketName)
      s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
    }
  }
}
