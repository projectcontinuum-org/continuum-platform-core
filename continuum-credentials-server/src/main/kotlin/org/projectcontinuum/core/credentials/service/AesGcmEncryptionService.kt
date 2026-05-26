package org.projectcontinuum.core.credentials.service

import org.projectcontinuum.core.credentials.config.EncryptionProperties
import org.projectcontinuum.core.credentials.exception.EncryptionException
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class AesGcmEncryptionService(
  private val encryptionProperties: EncryptionProperties
) : EncryptionService {

  companion object {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val KEY_ALGORITHM = "AES"
  }

  private val secretKey: SecretKeySpec by lazy {
    val keyBytes = Base64.getDecoder().decode(encryptionProperties.masterKey)
    require(keyBytes.size == 32) {
      "Master key must be exactly 32 bytes (256 bits) when Base64-decoded. Got ${keyBytes.size} bytes."
    }
    SecretKeySpec(keyBytes, KEY_ALGORITHM)
  }

  private val secureRandom = SecureRandom()

  override fun encrypt(plaintext: String): String {
    try {
      val iv = ByteArray(GCM_IV_LENGTH)
      secureRandom.nextBytes(iv)

      val cipher = Cipher.getInstance(ALGORITHM)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

      val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

      val combined = ByteArray(iv.size + ciphertext.size)
      System.arraycopy(iv, 0, combined, 0, iv.size)
      System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

      return Base64.getEncoder().encodeToString(combined)
    } catch (ex: Exception) {
      throw EncryptionException("Failed to encrypt credential data", ex)
    }
  }

  override fun decrypt(ciphertext: String): String {
    try {
      val combined = Base64.getDecoder().decode(ciphertext)

      val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
      val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

      val cipher = Cipher.getInstance(ALGORITHM)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

      val plaintext = cipher.doFinal(encrypted)
      return String(plaintext, Charsets.UTF_8)
    } catch (ex: Exception) {
      throw EncryptionException("Failed to decrypt credential data", ex)
    }
  }
}
