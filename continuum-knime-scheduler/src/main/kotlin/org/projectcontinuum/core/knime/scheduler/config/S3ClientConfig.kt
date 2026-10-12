package org.projectcontinuum.core.knime.scheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

/**
 * Selects the S3 client backend (AWS S3 or MinIO) based on a single shared property,
 * `continuum.core.knime-scheduler.storage.type`. Both conditional beans below intentionally
 * check the same property name — the equivalent config in
 * continuum-worker-springboot-starter's S3Config.kt checks two different property prefixes
 * for its two beans, which is a latent misconfiguration trap. Don't repeat that here.
 */
@Configuration
class S3ClientConfig {

  @Bean
  @ConditionalOnProperty(name = ["continuum.core.knime-scheduler.storage.type"], havingValue = "aws-s3")
  fun s3ClientAws(
    @Value("\${continuum.core.knime-scheduler.storage.bucket-region}")
    bucketRegion: String,
    @Value("\${continuum.core.knime-scheduler.storage.aws-profile-name}")
    awsProfileName: String
  ): S3Client {
    return S3Client.builder()
      .region(Region.of(bucketRegion))
      .credentialsProvider(
        ProfileCredentialsProvider.builder()
          .profileName(awsProfileName)
          .build()
      )
      .build()
  }

  @Bean
  @ConditionalOnProperty(name = ["continuum.core.knime-scheduler.storage.type"], havingValue = "minio")
  fun s3ClientMinio(
    @Value("\${continuum.core.knime-scheduler.storage.minio.endpoint}")
    minioEndpoint: String,
    @Value("\${continuum.core.knime-scheduler.storage.bucket-region}")
    bucketRegion: String,
    @Value("\${continuum.core.knime-scheduler.storage.minio.access-key}")
    minioAccessKey: String,
    @Value("\${continuum.core.knime-scheduler.storage.minio.secret-key}")
    minioSecretKey: String
  ): S3Client {
    return S3Client.builder()
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
}
