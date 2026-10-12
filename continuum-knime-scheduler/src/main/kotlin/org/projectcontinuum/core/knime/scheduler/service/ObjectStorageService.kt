package org.projectcontinuum.core.knime.scheduler.service

import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.io.InputStream

/**
 * The only component allowed to talk to the object store directly. Backed by either
 * AWS S3 or MinIO depending on which [software.amazon.awssdk.services.s3.S3Client] bean
 * is active — see [org.projectcontinuum.core.knime.scheduler.config.S3ClientConfig].
 */
interface ObjectStorageService {

  val bucketName: String

  fun putObject(key: String, inputStream: InputStream, contentLength: Long, contentType: String?)

  fun getObject(key: String): ResponseInputStream<GetObjectResponse>

  fun deleteObject(key: String)
}
