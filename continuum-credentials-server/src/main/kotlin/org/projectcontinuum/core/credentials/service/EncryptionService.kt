package org.projectcontinuum.core.credentials.service

interface EncryptionService {

  fun encrypt(plaintext: String): String

  fun decrypt(ciphertext: String): String
}
