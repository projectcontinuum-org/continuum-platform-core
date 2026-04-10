package org.projectcontinuum.core.cluster.manager.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.client.KubernetesClientException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.cluster.manager.exception.WorkbenchNotFoundException
import org.projectcontinuum.core.cluster.manager.model.ResourceSpec
import org.projectcontinuum.core.cluster.manager.model.WorkbenchResponse
import org.projectcontinuum.core.cluster.manager.model.WorkbenchStatus
import org.projectcontinuum.core.cluster.manager.service.DockerHubService
import org.projectcontinuum.core.cluster.manager.service.DockerHubTag
import org.projectcontinuum.core.cluster.manager.service.WorkbenchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant
import java.util.UUID

@WebMvcTest(WorkbenchController::class)
class WorkbenchControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workbenchService: WorkbenchService

  @MockitoBean
  private lateinit var dockerHubService: DockerHubService

  private val objectMapper = jacksonObjectMapper()

  private fun sampleResponse(
    instanceName: String = "my-workbench",
    status: String = WorkbenchStatus.RUNNING.name,
    namespace: String = "default",
    userId: String = "user-1"
  ) = WorkbenchResponse(
    instanceId = UUID.randomUUID(),
    instanceName = instanceName,
    namespace = namespace,
    userId = userId,
    status = status,
    image = "theiaide/theia:latest",
    resources = ResourceSpec(),
    serviceEndpoint = "wb-test-svc.$namespace.svc.cluster.local:8080",
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  // ── POST /api/v1/workbench ──────────────────────────────────────────

  @Test
  fun `POST creates workbench and returns 201`() {
    val response = sampleResponse()
    whenever(workbenchService.createWorkbench(eq("user-1"), any())).thenReturn(response)

    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"instanceName": "my-workbench"}""")
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
      .andExpect(jsonPath("$.status").value("RUNNING"))
  }

  @Test
  fun `POST without user-id header defaults to anonymous`() {
    val response = sampleResponse(userId = "anonymous")
    whenever(workbenchService.createWorkbench(eq("anonymous"), any())).thenReturn(response)

    mockMvc.perform(
      post("/api/v1/workbench")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"instanceName": "my-workbench"}""")
    )
      .andExpect(status().isCreated)
  }

  @Test
  fun `POST returns 400 when workbench already exists`() {
    whenever(workbenchService.createWorkbench(eq("user-1"), any()))
      .thenThrow(IllegalArgumentException("Workbench 'my-workbench' already exists for user 'user-1'"))

    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"instanceName": "my-workbench"}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Workbench 'my-workbench' already exists for user 'user-1'"))
  }

  @Test
  fun `POST returns 201 with full resource spec`() {
    val response = sampleResponse()
    whenever(workbenchService.createWorkbench(eq("user-1"), any())).thenReturn(response)

    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{
          "instanceName": "my-workbench",
          "image": "theiaide/theia:next",
          "resources": {
            "cpuRequest": "1",
            "cpuLimit": "4",
            "memoryRequest": "1Gi",
            "memoryLimit": "4Gi",
            "storageSize": "10Gi",
            "storageClassName": "fast-ssd"
          }
        }""")
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
  }

  @Test
  fun `POST returns 500 when K8s client fails`() {
    whenever(workbenchService.createWorkbench(eq("user-1"), any()))
      .thenThrow(KubernetesClientException("connection refused"))

    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"instanceName": "my-workbench"}""")
    )
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.error").value("Kubernetes operation failed: connection refused"))
  }

  @Test
  fun `POST returns 500 on unexpected exception`() {
    whenever(workbenchService.createWorkbench(eq("user-1"), any()))
      .thenThrow(RuntimeException("unexpected"))

    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"instanceName": "my-workbench"}""")
    )
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.error").value("Internal server error"))
  }

  // ── GET /api/v1/workbench/{instanceName} ────────────────────────────

  @Test
  fun `GET instance returns workbench status`() {
    val response = sampleResponse()
    whenever(workbenchService.getWorkbenchStatus("user-1", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      get("/api/v1/workbench/my-workbench")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
      .andExpect(jsonPath("$.status").value("RUNNING"))
  }

  @Test
  fun `GET instance returns 404 when not found`() {
    whenever(workbenchService.getWorkbenchStatus("user-1", "missing"))
      .thenThrow(WorkbenchNotFoundException("Workbench 'missing' not found for user 'user-1'"))

    mockMvc.perform(
      get("/api/v1/workbench/missing")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").value("Workbench 'missing' not found for user 'user-1'"))
  }

  @Test
  fun `GET instance without user-id header defaults to anonymous`() {
    val response = sampleResponse(userId = "anonymous")
    whenever(workbenchService.getWorkbenchStatus("anonymous", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      get("/api/v1/workbench/my-workbench")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.userId").value("anonymous"))
  }

  @Test
  fun `GET instance returns response with all fields`() {
    val instanceId = UUID.randomUUID()
    val now = Instant.parse("2026-01-15T10:30:00Z")
    val response = WorkbenchResponse(
      instanceId = instanceId,
      instanceName = "full-wb",
      namespace = "staging",
      userId = "user-1",
      status = WorkbenchStatus.PENDING.name,
      image = "theiaide/theia:1.0",
      resources = ResourceSpec(
        cpuRequest = "1",
        cpuLimit = "4",
        memoryRequest = "1Gi",
        memoryLimit = "4Gi",
        storageSize = "20Gi",
        storageClassName = "fast-ssd"
      ),
      serviceEndpoint = "wb-$instanceId-svc.staging.svc.cluster.local:8080",
      createdAt = now,
      updatedAt = now
    )
    whenever(workbenchService.getWorkbenchStatus("user-1", "full-wb")).thenReturn(response)

    mockMvc.perform(
      get("/api/v1/workbench/full-wb")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instanceId").value(instanceId.toString()))
      .andExpect(jsonPath("$.instanceName").value("full-wb"))
      .andExpect(jsonPath("$.namespace").value("staging"))
      .andExpect(jsonPath("$.userId").value("user-1"))
      .andExpect(jsonPath("$.status").value("PENDING"))
      .andExpect(jsonPath("$.image").value("theiaide/theia:1.0"))
      .andExpect(jsonPath("$.resources.cpuRequest").value("1"))
      .andExpect(jsonPath("$.resources.cpuLimit").value("4"))
      .andExpect(jsonPath("$.resources.memoryRequest").value("1Gi"))
      .andExpect(jsonPath("$.resources.memoryLimit").value("4Gi"))
      .andExpect(jsonPath("$.resources.storageSize").value("20Gi"))
      .andExpect(jsonPath("$.resources.storageClassName").value("fast-ssd"))
      .andExpect(jsonPath("$.serviceEndpoint").exists())
  }

  // ── DELETE /api/v1/workbench/{instanceName} ─────────────────────────

  @Test
  fun `DELETE returns 204`() {
    mockMvc.perform(
      delete("/api/v1/workbench/my-workbench")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNoContent)

    verify(workbenchService).deleteWorkbench("user-1", "my-workbench")
  }

  @Test
  fun `DELETE returns 404 when workbench not found`() {
    whenever(workbenchService.deleteWorkbench("user-1", "missing"))
      .thenThrow(WorkbenchNotFoundException("Workbench 'missing' not found for user 'user-1'"))

    mockMvc.perform(
      delete("/api/v1/workbench/missing")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `DELETE without user-id header defaults to anonymous`() {
    mockMvc.perform(
      delete("/api/v1/workbench/my-workbench")
    )
      .andExpect(status().isNoContent)

    verify(workbenchService).deleteWorkbench("anonymous", "my-workbench")
  }

  // ── GET /api/v1/workbench ───────────────────────────────────────────

  @Test
  fun `GET list returns workbenches for user`() {
    val responses = listOf(sampleResponse("wb-1"), sampleResponse("wb-2"))
    whenever(workbenchService.listWorkbenches("user-1")).thenReturn(responses)

    mockMvc.perform(
      get("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].instanceName").value("wb-1"))
      .andExpect(jsonPath("$[1].instanceName").value("wb-2"))
  }

  @Test
  fun `GET list returns empty array when no workbenches exist`() {
    whenever(workbenchService.listWorkbenches("user-1")).thenReturn(emptyList())

    mockMvc.perform(
      get("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }

  @Test
  fun `GET list without user-id header defaults to anonymous`() {
    whenever(workbenchService.listWorkbenches("anonymous")).thenReturn(emptyList())

    mockMvc.perform(
      get("/api/v1/workbench")
    )
      .andExpect(status().isOk)

    verify(workbenchService).listWorkbenches("anonymous")
  }

  // ── PUT /api/v1/workbench/{instanceName} ────────────────────────────

  @Test
  fun `PUT updates workbench config`() {
    val response = sampleResponse()
    whenever(workbenchService.updateWorkbench(eq("user-1"), eq("my-workbench"), any()))
      .thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"image": "theiaide/theia:next"}""")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
  }

  @Test
  fun `PUT returns 404 when workbench not found`() {
    whenever(workbenchService.updateWorkbench(eq("user-1"), eq("missing"), any()))
      .thenThrow(WorkbenchNotFoundException("Workbench 'missing' not found for user 'user-1'"))

    mockMvc.perform(
      put("/api/v1/workbench/missing")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"image": "theiaide/theia:next"}""")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `PUT with resource spec updates`() {
    val response = sampleResponse()
    whenever(workbenchService.updateWorkbench(eq("user-1"), eq("my-workbench"), any()))
      .thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{
          "resources": {
            "cpuRequest": "2",
            "cpuLimit": "8",
            "memoryRequest": "2Gi",
            "memoryLimit": "8Gi",
            "storageSize": "50Gi"
          }
        }""")
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `PUT without user-id header defaults to anonymous`() {
    val response = sampleResponse(userId = "anonymous")
    whenever(workbenchService.updateWorkbench(eq("anonymous"), eq("my-workbench"), any()))
      .thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"image": "theiaide/theia:next"}""")
    )
      .andExpect(status().isOk)

    verify(workbenchService).updateWorkbench(eq("anonymous"), eq("my-workbench"), any())
  }

  // ── PUT /api/v1/workbench/{instanceName}/suspend ────────────────────

  @Test
  fun `PUT suspend returns 200 with suspended response`() {
    val response = sampleResponse(status = WorkbenchStatus.SUSPENDED.name)
    whenever(workbenchService.suspendWorkbench("user-1", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/suspend")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
      .andExpect(jsonPath("$.status").value("SUSPENDED"))
  }

  @Test
  fun `PUT suspend returns 404 when workbench not found`() {
    whenever(workbenchService.suspendWorkbench("user-1", "missing"))
      .thenThrow(WorkbenchNotFoundException("Workbench 'missing' not found for user 'user-1'"))

    mockMvc.perform(
      put("/api/v1/workbench/missing/suspend")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `PUT suspend returns 400 when already suspended`() {
    whenever(workbenchService.suspendWorkbench("user-1", "my-workbench"))
      .thenThrow(IllegalArgumentException("Workbench 'my-workbench' is already suspended"))

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/suspend")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Workbench 'my-workbench' is already suspended"))
  }

  @Test
  fun `PUT suspend without user-id header defaults to anonymous`() {
    val response = sampleResponse(userId = "anonymous", status = WorkbenchStatus.SUSPENDED.name)
    whenever(workbenchService.suspendWorkbench("anonymous", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/suspend")
    )
      .andExpect(status().isOk)

    verify(workbenchService).suspendWorkbench("anonymous", "my-workbench")
  }

  // ── PUT /api/v1/workbench/{instanceName}/resume ─────────────────────

  @Test
  fun `PUT resume returns 200 with running response`() {
    val response = sampleResponse(status = WorkbenchStatus.RUNNING.name)
    whenever(workbenchService.resumeWorkbench("user-1", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/resume")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.instanceName").value("my-workbench"))
      .andExpect(jsonPath("$.status").value("RUNNING"))
  }

  @Test
  fun `PUT resume returns 404 when workbench not found`() {
    whenever(workbenchService.resumeWorkbench("user-1", "missing"))
      .thenThrow(WorkbenchNotFoundException("Workbench 'missing' not found for user 'user-1'"))

    mockMvc.perform(
      put("/api/v1/workbench/missing/resume")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `PUT resume returns 400 when workbench is not suspended`() {
    whenever(workbenchService.resumeWorkbench("user-1", "my-workbench"))
      .thenThrow(IllegalArgumentException("Workbench 'my-workbench' is not suspended"))

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/resume")
        .header("x-continuum-user-id", "user-1")
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value("Workbench 'my-workbench' is not suspended"))
  }

  @Test
  fun `PUT resume without user-id header defaults to anonymous`() {
    val response = sampleResponse(userId = "anonymous", status = WorkbenchStatus.RUNNING.name)
    whenever(workbenchService.resumeWorkbench("anonymous", "my-workbench")).thenReturn(response)

    mockMvc.perform(
      put("/api/v1/workbench/my-workbench/resume")
    )
      .andExpect(status().isOk)

    verify(workbenchService).resumeWorkbench("anonymous", "my-workbench")
  }

  // ── GET /api/v1/workbench/tags ──────────────────────────────────────

  @Test
  fun `GET tags returns available Docker Hub tags`() {
    val tags = listOf(
      DockerHubTag("0.0.5", "2024-01-01T00:00:00Z", 123456L),
      DockerHubTag("latest", "2024-01-02T00:00:00Z", 654321L)
    )
    whenever(dockerHubService.getAvailableTags()).thenReturn(tags)

    mockMvc.perform(get("/api/v1/workbench/tags"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(2))
      .andExpect(jsonPath("$[0].name").value("0.0.5"))
      .andExpect(jsonPath("$[0].lastUpdated").value("2024-01-01T00:00:00Z"))
      .andExpect(jsonPath("$[1].name").value("latest"))
  }

  @Test
  fun `GET tags returns 500 when Docker Hub is unreachable`() {
    whenever(dockerHubService.getAvailableTags())
      .thenThrow(RuntimeException("Failed to fetch Docker Hub tags"))

    mockMvc.perform(get("/api/v1/workbench/tags"))
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.error").value("Internal server error"))
  }
}
