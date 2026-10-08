package org.projectcontinuum.core.cluster.manager.controller

import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerCreateRequest
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerResponse
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerUpdateRequest
import org.projectcontinuum.core.cluster.manager.service.FeatureWorkerService
import org.projectcontinuum.core.cluster.manager.service.OverlayService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/feature-workers")
class FeatureWorkerController(
  private val featureWorkerService: FeatureWorkerService,
  @Qualifier("featureWorkerOverlayService") private val overlayService: OverlayService
) {

  @GetMapping("/variants")
  fun getAvailableVariants(): ResponseEntity<List<String>> {
    val variants = overlayService.listVariants()
    return ResponseEntity.ok(variants)
  }

  @PostMapping
  fun createFeatureWorker(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @RequestBody request: FeatureWorkerCreateRequest
  ): ResponseEntity<FeatureWorkerResponse> {
    val response = featureWorkerService.createFeatureWorker(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping("/{workerName}")
  fun getFeatureWorkerStatus(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workerName: String
  ): ResponseEntity<FeatureWorkerResponse> {
    val response = featureWorkerService.getFeatureWorkerStatus(userId, workerName)
    return ResponseEntity.ok(response)
  }

  @DeleteMapping("/{workerName}")
  fun deleteFeatureWorker(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workerName: String
  ): ResponseEntity<Void> {
    featureWorkerService.deleteFeatureWorker(userId, workerName)
    return ResponseEntity.noContent().build()
  }

  @GetMapping
  fun listFeatureWorkers(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String
  ): ResponseEntity<List<FeatureWorkerResponse>> {
    val response = featureWorkerService.listFeatureWorkers(userId)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{workerName}")
  fun updateFeatureWorker(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workerName: String,
    @RequestBody request: FeatureWorkerUpdateRequest
  ): ResponseEntity<FeatureWorkerResponse> {
    val response = featureWorkerService.updateFeatureWorker(userId, workerName, request)
    return ResponseEntity.ok(response)
  }
}
