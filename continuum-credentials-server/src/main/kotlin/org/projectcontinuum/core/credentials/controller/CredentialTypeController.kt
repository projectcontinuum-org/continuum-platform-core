package org.projectcontinuum.core.credentials.controller

import jakarta.validation.Valid
import org.projectcontinuum.core.credentials.model.CredentialTypeCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialTypeResponse
import org.projectcontinuum.core.credentials.service.CredentialTypeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/credential-types")
class CredentialTypeController(
  private val credentialTypeService: CredentialTypeService
) {

  @PostMapping
  fun createType(
    @Valid @RequestBody request: CredentialTypeCreateRequest
  ): ResponseEntity<CredentialTypeResponse> {
    val response = credentialTypeService.createType(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping
  fun listTypes(): ResponseEntity<List<CredentialTypeResponse>> {
    val response = credentialTypeService.listTypes()
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{type}")
  fun getTypeVersions(@PathVariable type: String): ResponseEntity<List<CredentialTypeResponse>> {
    val response = credentialTypeService.getTypeVersions(type)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{type}/{version}")
  fun getType(
    @PathVariable type: String,
    @PathVariable version: String
  ): ResponseEntity<CredentialTypeResponse> {
    val response = credentialTypeService.getType(type, version)
    return ResponseEntity.ok(response)
  }
}
