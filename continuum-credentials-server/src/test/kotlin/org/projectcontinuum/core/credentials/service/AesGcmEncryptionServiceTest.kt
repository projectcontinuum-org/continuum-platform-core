package org.projectcontinuum.core.credentials.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.credentials.config.EncryptionProperties
import org.projectcontinuum.core.credentials.exception.EncryptionException

class AesGcmEncryptionServiceTest {

  private val validKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

  private fun createService(masterKey: String = validKey): AesGcmEncryptionService {
    return AesGcmEncryptionService(EncryptionProperties(masterKey = masterKey))
  }

  @Test
  fun `encrypt and decrypt round-trip returns original plaintext`() {
    val service = createService()
    val plaintext = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"

    val encrypted = service.encrypt(plaintext)
    val decrypted = service.decrypt(encrypted)

    assertEquals(plaintext, decrypted)
  }

  @Test
  fun `encrypt produces different ciphertext each time due to random IV`() {
    val service = createService()
    val plaintext = "same-plaintext"

    val encrypted1 = service.encrypt(plaintext)
    val encrypted2 = service.encrypt(plaintext)

    assertNotEquals(encrypted1, encrypted2)
  }

  @Test
  fun `encrypt produces base64 encoded output`() {
    val service = createService()
    val encrypted = service.encrypt("test")

    assertDoesNotThrow {
      java.util.Base64.getDecoder().decode(encrypted)
    }
  }

  @Test
  fun `decrypt with tampered ciphertext throws EncryptionException`() {
    val service = createService()
    val encrypted = service.encrypt("test-data")

    val tampered = encrypted.substring(0, encrypted.length - 4) + "XXXX"

    assertThrows(EncryptionException::class.java) {
      service.decrypt(tampered)
    }
  }

  @Test
  fun `decrypt with invalid base64 throws EncryptionException`() {
    val service = createService()

    assertThrows(EncryptionException::class.java) {
      service.decrypt("not-valid-base64!!!")
    }
  }

  @Test
  fun `invalid key length throws exception on first use`() {
    val shortKey = java.util.Base64.getEncoder().encodeToString(ByteArray(16))
    val service = createService(masterKey = shortKey)

    assertThrows(EncryptionException::class.java) {
      service.encrypt("test")
    }
  }

  @Test
  fun `encrypts and decrypts empty string`() {
    val service = createService()
    val encrypted = service.encrypt("")
    val decrypted = service.decrypt(encrypted)

    assertEquals("", decrypted)
  }

  @Test
  fun `encrypts and decrypts unicode content`() {
    val service = createService()
    val plaintext = "value with émojis 🔑 and 中文"

    val encrypted = service.encrypt(plaintext)
    val decrypted = service.decrypt(encrypted)

    assertEquals(plaintext, decrypted)
  }
}
