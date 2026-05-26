package org.projectcontinuum.core.credentials.controller

import jakarta.validation.Valid
import org.projectcontinuum.core.credentials.model.CredentialCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialResponse
import org.projectcontinuum.core.credentials.model.CredentialUpdateRequest
import org.projectcontinuum.core.credentials.service.CredentialService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/credentials")
class CredentialController(
  private val credentialService: CredentialService
) {

  @PostMapping
  fun createCredential(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Valid @RequestBody request: CredentialCreateRequest
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.createCredential(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping
  fun listCredentials(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.listCredentials(userId)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{name}")
  fun getCredential(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable name: String
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.getCredential(userId, name)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/type/{type}")
  fun getCredentialsByType(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable type: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.getCredentialsByType(userId, type)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/type/{type}/{version}")
  fun getCredentialsByTypeAndVersion(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable type: String,
    @PathVariable version: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.getCredentialsByTypeAndVersion(userId, type, version)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{name}")
  fun updateCredential(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable name: String,
    @Valid @RequestBody request: CredentialUpdateRequest
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.updateCredential(userId, name, request)
    return ResponseEntity.ok(response)
  }

  @DeleteMapping("/{name}")
  fun deleteCredential(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable name: String
  ): ResponseEntity<Void> {
    credentialService.deleteCredential(userId, name)
    return ResponseEntity.noContent().build()
  }
}
