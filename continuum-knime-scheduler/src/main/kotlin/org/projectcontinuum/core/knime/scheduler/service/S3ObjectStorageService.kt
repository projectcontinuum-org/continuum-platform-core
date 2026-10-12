package org.projectcontinuum.core.knime.scheduler.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

@Service
class S3ObjectStorageService(
  private val s3Client: S3Client,
  @Value("\${continuum.core.knime-scheduler.storage.bucket-name}")
  override val bucketName: String
) : ObjectStorageService {

  override fun putObject(key: String, inputStream: InputStream, contentLength: Long, contentType: String?) {
    val request = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .contentType(contentType)
      .build()
    s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength))
  }

  override fun getObject(key: String): ResponseInputStream<GetObjectResponse> {
    val request = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build()
    return s3Client.getObject(request)
  }

  override fun deleteObject(key: String) {
    val request = DeleteObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build()
    s3Client.deleteObject(request)
  }
}
