package org.projectcontinuum.core.cluster.manager.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.client.KubernetesClientException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.cluster.manager.exception.FeatureWorkerNotFoundException
import org.projectcontinuum.core.cluster.manager.model.AutoscalingSpec
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerResourceSpec
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerResponse
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerStatus
import org.projectcontinuum.core.cluster.manager.service.FeatureWorkerService
import org.projectcontinuum.core.cluster.manager.service.OverlayService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

@WebMvcTest(FeatureWorkerController::class)
class FeatureWorkerControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var featureWorkerService: FeatureWorkerService

  @MockitoBean(name = "featureWorkerOverlayService")
  private lateinit var overlayService: OverlayService

  private val objectMapper = jacksonObjectMapper()

  private fun sampleResponse(
    workerName: String = "my-worker",
    status: String = FeatureWorkerStatus.RUNNING.name,
    namespace: String = "default",
    createdBy: String = "user-1",
    taskQueue: String = "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"
  ) = FeatureWorkerResponse(
    workerId = UUID.randomUUID(),
    workerName = workerName,
    namespace = namespace,
    createdBy = createdBy,
    status = status,
    image = "projectcontinuum/feature-worker:latest",
    taskQueue = taskQueue,
    replicas = 1,
    autoscaling = AutoscalingSpec(),
    resources = FeatureWorkerResourceSpec(),
    pvcEnabled = false,
    envVars = emptyMap(),
    overlayVariant = null,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  // ── POST /api/v1/feature-workers ────────────────────────────────────

  @Test
  fun `POST creates feature worker and returns 201`() {
    val response = sampleResponse()
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any())).thenReturn(response)

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.workerName").value("my-worker"))
      .andExpect(jsonPath("$.status").value("RUNNING"))
  }

  @Test
  fun `POST without user-id header defaults to anonymous`() {
    val response = sampleResponse(createdBy = "anonymous")
    whenever(featureWorkerService.createFeatureWorker(eq("anonymous"), any())).thenReturn(response)

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isCreated)

    verify(featureWorkerService).createFeatureWorker(eq("anonymous"), any())
  }

  @Test
  fun `POST returns 400 when feature worker already exists`() {
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any()))
      .thenThrow(IllegalArgumentException("Feature worker 'my-worker' already exists in namespace 'default'"))

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Feature worker 'my-worker' already exists in namespace 'default'"))
  }

  @Test
  fun `POST returns 400 when task queue format is invalid`() {
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any()))
      .thenThrow(IllegalArgumentException("Task queue 'bad-queue' is invalid. It must match the pattern CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE"))

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "bad-queue"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Task queue 'bad-queue' is invalid. It must match the pattern CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE"))
  }

  @Test
  fun `POST returns 400 when task queue is already in use`() {
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any()))
      .thenThrow(IllegalArgumentException("Task queue 'CONTINUUM-FEATURE-SHARED-TASK-QUEUE' is already in use by feature worker 'other-worker'"))

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-SHARED-TASK-QUEUE"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Task queue 'CONTINUUM-FEATURE-SHARED-TASK-QUEUE' is already in use by feature worker 'other-worker'"))
  }

  @Test
  fun `POST returns 500 when K8s client fails`() {
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any()))
      .thenThrow(KubernetesClientException("connection refused"))

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.error").value("Kubernetes operation failed: connection refused"))
  }

  @Test
  fun `POST returns 500 on unexpected exception`() {
    whenever(featureWorkerService.createFeatureWorker(eq("user-1"), any()))
      .thenThrow(RuntimeException("unexpected"))

    mockMvc.perform(
      post("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"workerName": "my-worker", "image": "projectcontinuum/feature-worker:latest", "taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.error").value("Internal server error"))
  }

  // ── GET /api/v1/feature-workers/{workerName} ────────────────────────

  @Test
  fun `GET worker returns feature worker status`() {
    val response = sampleResponse()
    whenever(featureWorkerService.getFeatureWorkerStatus("user-1", "my-worker")).thenReturn(response)

    mockMvc.perform(
      get("/api/v1/feature-workers/my-worker")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.workerName").value("my-worker"))
      .andExpect(jsonPath("$.status").value("RUNNING"))
  }

  @Test
  fun `GET worker returns 404 when not found`() {
    whenever(featureWorkerService.getFeatureWorkerStatus("user-1", "missing"))
      .thenThrow(FeatureWorkerNotFoundException("Feature worker 'missing' not found"))

    mockMvc.perform(
      get("/api/v1/feature-workers/missing")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").value("Feature worker 'missing' not found"))
  }

  @Test
  fun `GET worker without user-id header defaults to anonymous`() {
    val response = sampleResponse()
    whenever(featureWorkerService.getFeatureWorkerStatus("anonymous", "my-worker")).thenReturn(response)

    mockMvc.perform(
      get("/api/v1/feature-workers/my-worker")
    )
      .andExpect(status().isOk)

    verify(featureWorkerService).getFeatureWorkerStatus("anonymous", "my-worker")
  }

  // ── DELETE /api/v1/feature-workers/{workerName} ─────────────────────

  @Test
  fun `DELETE returns 204`() {
    mockMvc.perform(
      delete("/api/v1/feature-workers/my-worker")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNoContent)

    verify(featureWorkerService).deleteFeatureWorker("user-1", "my-worker")
  }

  @Test
  fun `DELETE returns 404 when feature worker not found`() {
    whenever(featureWorkerService.deleteFeatureWorker("user-1", "missing"))
      .thenThrow(FeatureWorkerNotFoundException("Feature worker 'missing' not found"))

    mockMvc.perform(
      delete("/api/v1/feature-workers/missing")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `DELETE without user-id header defaults to anonymous`() {
    mockMvc.perform(
      delete("/api/v1/feature-workers/my-worker")
    )
      .andExpect(status().isNoContent)

    verify(featureWorkerService).deleteFeatureWorker("anonymous", "my-worker")
  }

  // ── GET /api/v1/feature-workers ─────────────────────────────────────

  @Test
  fun `GET list returns all feature workers`() {
    val responses = listOf(sampleResponse("fw-1"), sampleResponse("fw-2"))
    whenever(featureWorkerService.listFeatureWorkers("user-1")).thenReturn(responses)

    mockMvc.perform(
      get("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].workerName").value("fw-1"))
      .andExpect(jsonPath("$[1].workerName").value("fw-2"))
  }

  @Test
  fun `GET list returns empty array when none exist`() {
    whenever(featureWorkerService.listFeatureWorkers("user-1")).thenReturn(emptyList())

    mockMvc.perform(
      get("/api/v1/feature-workers")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }

  @Test
  fun `GET list without user-id header defaults to anonymous`() {
    whenever(featureWorkerService.listFeatureWorkers("anonymous")).thenReturn(emptyList())

    mockMvc.perform(
      get("/api/v1/feature-workers")
    )
      .andExpect(status().isOk)

    verify(featureWorkerService).listFeatureWorkers("anonymous")
  }

  // ── PUT /api/v1/feature-workers/{workerName} ────────────────────────

  @Test
  fun `PUT updates feature worker config`() {
    val response = sampleResponse()
    whenever(featureWorkerService.updateFeatureWorker(eq("user-1"), eq("my-worker"), any()))
      .thenReturn(response)

    mockMvc.perform(
      put("/api/v1/feature-workers/my-worker")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE", "image": "projectcontinuum/feature-worker:v2"}""")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.workerName").value("my-worker"))
  }

  @Test
  fun `PUT returns 404 when feature worker not found`() {
    whenever(featureWorkerService.updateFeatureWorker(eq("user-1"), eq("missing"), any()))
      .thenThrow(FeatureWorkerNotFoundException("Feature worker 'missing' not found"))

    mockMvc.perform(
      put("/api/v1/feature-workers/missing")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"taskQueue": "CONTINUUM-FEATURE-MISSING-TASK-QUEUE"}""")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `PUT returns 400 when task queue format is invalid`() {
    whenever(featureWorkerService.updateFeatureWorker(eq("user-1"), eq("my-worker"), any()))
      .thenThrow(IllegalArgumentException("Task queue 'bad-queue' is invalid. It must match the pattern CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE"))

    mockMvc.perform(
      put("/api/v1/feature-workers/my-worker")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"taskQueue": "bad-queue"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Task queue 'bad-queue' is invalid. It must match the pattern CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE"))
  }

  @Test
  fun `PUT returns 400 when task queue already used by another worker`() {
    whenever(featureWorkerService.updateFeatureWorker(eq("user-1"), eq("my-worker"), any()))
      .thenThrow(IllegalArgumentException("Task queue 'CONTINUUM-FEATURE-TAKEN-TASK-QUEUE' is already in use by feature worker 'other-worker'"))

    mockMvc.perform(
      put("/api/v1/feature-workers/my-worker")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"taskQueue": "CONTINUUM-FEATURE-TAKEN-TASK-QUEUE"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Task queue 'CONTINUUM-FEATURE-TAKEN-TASK-QUEUE' is already in use by feature worker 'other-worker'"))
  }

  @Test
  fun `PUT without user-id header defaults to anonymous`() {
    val response = sampleResponse(createdBy = "anonymous")
    whenever(featureWorkerService.updateFeatureWorker(eq("anonymous"), eq("my-worker"), any()))
      .thenReturn(response)

    mockMvc.perform(
      put("/api/v1/feature-workers/my-worker")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"taskQueue": "CONTINUUM-FEATURE-MY-WORKER-TASK-QUEUE"}""")
    )
      .andExpect(status().isOk)

    verify(featureWorkerService).updateFeatureWorker(eq("anonymous"), eq("my-worker"), any())
  }

  // ── GET /api/v1/feature-workers/variants ────────────────────────────

  @Test
  fun `GET variants returns overlay variant names`() {
    whenever(overlayService.listVariants()).thenReturn(listOf("gpu", "high-memory"))

    mockMvc.perform(get("/api/v1/feature-workers/variants"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0]").value("gpu"))
      .andExpect(jsonPath("$[1]").value("high-memory"))
  }

  @Test
  fun `GET variants returns empty list when overlays disabled`() {
    whenever(overlayService.listVariants()).thenReturn(emptyList())

    mockMvc.perform(get("/api/v1/feature-workers/variants"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }
}
