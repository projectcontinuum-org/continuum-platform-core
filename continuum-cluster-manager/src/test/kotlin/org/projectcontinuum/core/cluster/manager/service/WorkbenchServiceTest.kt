package org.projectcontinuum.core.cluster.manager.service

import freemarker.template.Configuration
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties
import org.projectcontinuum.core.cluster.manager.entity.WorkbenchInstanceEntity
import org.projectcontinuum.core.cluster.manager.exception.WorkbenchNotFoundException
import org.projectcontinuum.core.cluster.manager.model.*
import org.projectcontinuum.core.cluster.manager.repository.WorkbenchInstanceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@SpringBootTest
@EnableKubernetesMockClient(crud = true)
@ActiveProfiles("test")
class WorkbenchServiceTest {

  @Autowired
  private lateinit var repository: WorkbenchInstanceRepository

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  lateinit var client: KubernetesClient
  lateinit var server: KubernetesMockServer

  private lateinit var service: WorkbenchService
  private lateinit var workbenchProperties: WorkbenchProperties

  @BeforeEach
  fun setUp() {
    repository.deleteAll()

    val freemarkerConfig = Configuration(Configuration.VERSION_2_3_34)
    freemarkerConfig.setClassLoaderForTemplateLoading(this::class.java.classLoader, "/templates")
    freemarkerConfig.defaultEncoding = "UTF-8"

    workbenchProperties = WorkbenchProperties(
      defaultImage = "projectcontinuum/continuum-workbench:latest",
      namespace = "default"
    )

    service = WorkbenchService(repository, client, freemarkerConfig, transactionTemplate, workbenchProperties)
  }

  private fun createSampleEntity(
    instanceName: String = "test-wb",
    namespace: String = "default",
    userId: String = "user-1",
    status: String = WorkbenchStatus.RUNNING.name,
    instanceId: UUID = UUID.randomUUID()
  ): WorkbenchInstanceEntity {
    val now = Instant.now()
    return WorkbenchInstanceEntity(
      instanceId = instanceId,
      instanceName = instanceName,
      namespace = namespace,
      userId = userId,
      status = status,
      image = "theiaide/theia:latest",
      createdAt = now,
      updatedAt = now
    )
  }

  // ── createWorkbench ─────────────────────────────────────────────────

  @Test
  fun `createWorkbench saves entity and creates K8s resources`() {
    val request = WorkbenchCreateRequest(
      instanceName = "test-wb",
      resources = ResourceSpec(),
      image = "theiaide/theia:latest"
    )

    val response = service.createWorkbench("user-1", request)

    assertEquals("test-wb", response.instanceName)
    assertEquals("user-1", response.userId)
    assertEquals("default", response.namespace)
    assertEquals(WorkbenchStatus.RUNNING.name, response.status)
    assertNotNull(response.instanceId)
    assertNotNull(response.serviceEndpoint)

    val saved = repository.findByUserIdAndInstanceName("user-1", "test-wb")
    assertNotNull(saved)
    assertEquals(response.instanceId, saved!!.instanceId)

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", response.instanceId.toString()).list().items
    assertEquals(1, deployments.size)

    val services = client.services().inNamespace("default")
      .withLabel("instance-id", response.instanceId.toString()).list().items
    assertEquals(1, services.size)

    val pvcs = client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", response.instanceId.toString()).list().items
    assertEquals(1, pvcs.size)
  }

  @Test
  fun `createWorkbench throws when workbench with same name already exists`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "dup-wb"))

    val exception = assertThrows<IllegalArgumentException> {
      service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "dup-wb"))
    }
    assertTrue(exception.message!!.contains("already exists"))
  }

  @Test
  fun `createWorkbench with custom resources sets correct values`() {
    val customResources = ResourceSpec(
      cpuRequest = "2",
      cpuLimit = "4",
      memoryRequest = "2Gi",
      memoryLimit = "8Gi",
      storageSize = "50Gi",
      storageClassName = "fast-ssd"
    )
    val request = WorkbenchCreateRequest(
      instanceName = "custom-wb",
      resources = customResources,
      image = "theiaide/theia:custom"
    )

    val response = service.createWorkbench("user-1", request)

    assertEquals("custom-wb", response.instanceName)
    assertEquals("default", response.namespace) // Uses configured namespace
    assertEquals("theiaide/theia:custom", response.image)
    assertEquals("2", response.resources.cpuRequest)
    assertEquals("4", response.resources.cpuLimit)
    assertEquals("2Gi", response.resources.memoryRequest)
    assertEquals("8Gi", response.resources.memoryLimit)
    assertEquals("50Gi", response.resources.storageSize)
    assertEquals("fast-ssd", response.resources.storageClassName)
  }

  @Test
  fun `createWorkbench with default request values uses defaults`() {
    val request = WorkbenchCreateRequest(instanceName = "default-wb")

    val response = service.createWorkbench("user-1", request)

    assertEquals("default", response.namespace)
    assertEquals("projectcontinuum/continuum-workbench:0.0.5", response.image)
    assertEquals("500m", response.resources.cpuRequest)
    assertEquals("2", response.resources.cpuLimit)
    assertEquals("512Mi", response.resources.memoryRequest)
    assertEquals("1Gi", response.resources.memoryLimit)
    assertEquals("5Gi", response.resources.storageSize)
    assertNull(response.resources.storageClassName)
  }

  @Test
  fun `createWorkbench generates unique instanceId`() {
    val response1 = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "wb-a"))
    val response2 = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "wb-b"))

    assertNotEquals(response1.instanceId, response2.instanceId)
  }

  @Test
  fun `createWorkbench generates correct service endpoint format`() {
    val request = WorkbenchCreateRequest(instanceName = "ep-wb")
    val response = service.createWorkbench("user-1", request)

    val expectedEndpoint = "wb-${response.instanceId}-svc.default.svc.cluster.local:8080"
    assertEquals(expectedEndpoint, response.serviceEndpoint)
  }

  @Test
  fun `createWorkbench allows re-creation for different users with same instance name`() {
    val response1 = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "shared-name"))
    val response2 = service.createWorkbench("user-2", WorkbenchCreateRequest(instanceName = "shared-name"))

    assertNotEquals(response1.instanceId, response2.instanceId)
    assertEquals("user-1", response1.userId)
    assertEquals("user-2", response2.userId)
  }

  @Test
  fun `createWorkbench sets timestamps on entity`() {
    val beforeCreate = Instant.now()
    val request = WorkbenchCreateRequest(instanceName = "ts-wb")
    val response = service.createWorkbench("user-1", request)

    val entity = repository.findById(response.instanceId).get()
    assertFalse(entity.createdAt.isBefore(beforeCreate))
    assertFalse(entity.updatedAt.isBefore(beforeCreate))
  }

  @Test
  fun `createWorkbench uses configured namespace`() {
    val request = WorkbenchCreateRequest(instanceName = "ns-test-wb")
    val response = service.createWorkbench("user-1", request)

    assertEquals(workbenchProperties.namespace, response.namespace)
  }

  // ── getWorkbenchStatus ──────────────────────────────────────────────

  @Test
  fun `getWorkbenchStatus returns response for existing workbench`() {
    val instanceId = UUID.randomUUID()
    val entity = createSampleEntity(instanceName = "status-wb", instanceId = instanceId)
    repository.save(entity)

    val response = service.getWorkbenchStatus("user-1", "status-wb")

    assertEquals("status-wb", response.instanceName)
    assertEquals("user-1", response.userId)
  }

  @Test
  fun `getWorkbenchStatus throws WorkbenchNotFoundException for missing workbench`() {
    assertThrows<WorkbenchNotFoundException> {
      service.getWorkbenchStatus("user-1", "nonexistent")
    }
  }

  @Test
  fun `getWorkbenchStatus refreshes status from K8s when deployment has ready replicas`() {
    val instanceId = UUID.randomUUID()
    val entity = createSampleEntity(
      instanceName = "refresh-wb",
      instanceId = instanceId,
      status = WorkbenchStatus.PENDING.name
    )
    repository.save(entity)

    // Create a deployment in K8s with ready replicas
    val deployment = DeploymentBuilder()
      .withNewMetadata()
      .withName("wb-$instanceId-deployment")
      .withNamespace("default")
      .endMetadata()
      .withStatus(DeploymentStatusBuilder().withReadyReplicas(1).build())
      .build()
    client.apps().deployments().inNamespace("default").resource(deployment).create()

    val response = service.getWorkbenchStatus("user-1", "refresh-wb")
    assertEquals(WorkbenchStatus.RUNNING.name, response.status)
  }

  @Test
  fun `getWorkbenchStatus sets PENDING when deployment has zero ready replicas`() {
    val instanceId = UUID.randomUUID()
    val entity = createSampleEntity(
      instanceName = "pending-wb",
      instanceId = instanceId,
      status = WorkbenchStatus.RUNNING.name
    )
    repository.save(entity)

    // Create a deployment with 0 ready replicas
    val deployment = DeploymentBuilder()
      .withNewMetadata()
      .withName("wb-$instanceId-deployment")
      .withNamespace("default")
      .endMetadata()
      .withStatus(DeploymentStatusBuilder().withReadyReplicas(0).build())
      .build()
    client.apps().deployments().inNamespace("default").resource(deployment).create()

    val response = service.getWorkbenchStatus("user-1", "pending-wb")
    assertEquals(WorkbenchStatus.PENDING.name, response.status)
  }

  @Test
  fun `getWorkbenchStatus sets UNKNOWN when no deployment found`() {
    val instanceId = UUID.randomUUID()
    val entity = createSampleEntity(
      instanceName = "unknown-wb",
      instanceId = instanceId,
      status = WorkbenchStatus.RUNNING.name
    )
    repository.save(entity)

    // No K8s deployment exists for this workbench
    val response = service.getWorkbenchStatus("user-1", "unknown-wb")
    assertEquals(WorkbenchStatus.UNKNOWN.name, response.status)
  }

  @Test
  fun `getWorkbenchStatus does not update when status unchanged`() {
    val instanceId = UUID.randomUUID()
    val entity = createSampleEntity(
      instanceName = "unchanged-wb",
      instanceId = instanceId,
      status = WorkbenchStatus.UNKNOWN.name
    )
    repository.save(entity)
    val versionAfterSave = repository.findById(instanceId).get().entityVersion

    // No deployment means UNKNOWN - same as current status
    val response = service.getWorkbenchStatus("user-1", "unchanged-wb")
    assertEquals(WorkbenchStatus.UNKNOWN.name, response.status)

    // Verify entity version didn't change (no unnecessary save)
    val dbEntity = repository.findById(instanceId).get()
    assertEquals(versionAfterSave, dbEntity.entityVersion)
  }

  // ── deleteWorkbench ─────────────────────────────────────────────────

  @Test
  fun `deleteWorkbench soft-deletes DB record and removes K8s resources`() {
    val request = WorkbenchCreateRequest(instanceName = "delete-wb")
    val created = service.createWorkbench("user-1", request)

    service.deleteWorkbench("user-1", "delete-wb")

    // Should not appear in active queries
    assertNull(repository.findByUserIdAndInstanceName("user-1", "delete-wb"))

    // But record still exists in DB with DELETED status
    val dbRecord = repository.findById(created.instanceId)
    assertTrue(dbRecord.isPresent)
    assertEquals("DELETED", dbRecord.get().status)

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", created.instanceId.toString()).list().items
    assertEquals(0, deployments.size)
  }

  @Test
  fun `deleteWorkbench throws WorkbenchNotFoundException for missing workbench`() {
    assertThrows<WorkbenchNotFoundException> {
      service.deleteWorkbench("user-1", "nonexistent")
    }
  }

  @Test
  fun `deleteWorkbench removes all K8s resources - deployment, service, and pvc`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "full-del-wb"))
    val instanceId = created.instanceId.toString()

    // Verify resources exist before delete
    assertEquals(1, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(1, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(1, client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)

    service.deleteWorkbench("user-1", "full-del-wb")

    // Verify all resources are gone
    assertEquals(0, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(0, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(0, client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
  }

  @Test
  fun `deleted workbenches are excluded from list queries`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "keep-wb"))
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "remove-wb"))

    assertEquals(2, service.listWorkbenches("user-1").size)

    service.deleteWorkbench("user-1", "remove-wb")

    val remaining = service.listWorkbenches("user-1")
    assertEquals(1, remaining.size)
    assertEquals("keep-wb", remaining[0].instanceName)
  }

  // ── listWorkbenches ─────────────────────────────────────────────────

  @Test
  fun `listWorkbenches returns all workbenches for user`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "wb-1"))
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "wb-2"))
    service.createWorkbench("user-2", WorkbenchCreateRequest(instanceName = "wb-3"))

    val user1Workbenches = service.listWorkbenches("user-1")
    assertEquals(2, user1Workbenches.size)

    val user2Workbenches = service.listWorkbenches("user-2")
    assertEquals(1, user2Workbenches.size)
  }

  @Test
  fun `listWorkbenches returns empty list for unknown user`() {
    val workbenches = service.listWorkbenches("unknown-user")
    assertTrue(workbenches.isEmpty())
  }

  @Test
  fun `listWorkbenches returns correct data in responses`() {
    val request = WorkbenchCreateRequest(
      instanceName = "detail-wb",
      image = "theiaide/theia:v2",
      resources = ResourceSpec(cpuRequest = "1", memoryRequest = "2Gi")
    )
    service.createWorkbench("user-1", request)

    val results = service.listWorkbenches("user-1")
    assertEquals(1, results.size)

    val wb = results[0]
    assertEquals("detail-wb", wb.instanceName)
    assertEquals("default", wb.namespace)
    assertEquals("user-1", wb.userId)
    assertEquals("theiaide/theia:v2", wb.image)
    assertEquals("1", wb.resources.cpuRequest)
    assertEquals("2Gi", wb.resources.memoryRequest)
    assertNotNull(wb.instanceId)
    assertNotNull(wb.serviceEndpoint)
    assertNotNull(wb.createdAt)
    assertNotNull(wb.updatedAt)
  }

  // ── updateWorkbench ─────────────────────────────────────────────────

  @Test
  fun `updateWorkbench updates image and resources`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "update-wb"))

    val updateRequest = WorkbenchUpdateRequest(
      image = "theiaide/theia:next",
      resources = ResourceSpec(cpuRequest = "1", memoryRequest = "1Gi")
    )
    val updated = service.updateWorkbench("user-1", "update-wb", updateRequest)

    assertEquals("theiaide/theia:next", updated.image)
    assertEquals("1", updated.resources.cpuRequest)
    assertEquals("1Gi", updated.resources.memoryRequest)
  }

  @Test
  fun `updateWorkbench throws WorkbenchNotFoundException for missing workbench`() {
    assertThrows<WorkbenchNotFoundException> {
      service.updateWorkbench("user-1", "nonexistent", WorkbenchUpdateRequest())
    }
  }

  @Test
  fun `updateWorkbench with only image preserves existing resources`() {
    val originalResources = ResourceSpec(
      cpuRequest = "2",
      cpuLimit = "4",
      memoryRequest = "2Gi",
      memoryLimit = "4Gi",
      storageSize = "10Gi",
      storageClassName = "standard"
    )
    service.createWorkbench("user-1", WorkbenchCreateRequest(
      instanceName = "partial-wb",
      resources = originalResources
    ))

    val updated = service.updateWorkbench(
      "user-1", "partial-wb",
      WorkbenchUpdateRequest(image = "theiaide/theia:v3")
    )

    assertEquals("theiaide/theia:v3", updated.image)
    assertEquals("2", updated.resources.cpuRequest)
    assertEquals("4", updated.resources.cpuLimit)
    assertEquals("2Gi", updated.resources.memoryRequest)
    assertEquals("4Gi", updated.resources.memoryLimit)
    assertEquals("10Gi", updated.resources.storageSize)
    assertEquals("standard", updated.resources.storageClassName)
  }

  @Test
  fun `updateWorkbench with only resources preserves existing image`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(
      instanceName = "res-only-wb",
      image = "theiaide/theia:original"
    ))

    val updated = service.updateWorkbench(
      "user-1", "res-only-wb",
      WorkbenchUpdateRequest(resources = ResourceSpec(cpuRequest = "4"))
    )

    assertEquals("theiaide/theia:original", updated.image)
    assertEquals("4", updated.resources.cpuRequest)
  }

  @Test
  fun `updateWorkbench with empty request preserves all existing values`() {
    val original = service.createWorkbench("user-1", WorkbenchCreateRequest(
      instanceName = "noop-wb",
      image = "theiaide/theia:original"
    ))

    val updated = service.updateWorkbench(
      "user-1", "noop-wb",
      WorkbenchUpdateRequest()
    )

    assertEquals(original.image, updated.image)
    assertEquals(original.resources.cpuRequest, updated.resources.cpuRequest)
    assertEquals(original.resources.cpuLimit, updated.resources.cpuLimit)
    assertEquals(original.resources.memoryRequest, updated.resources.memoryRequest)
    assertEquals(original.resources.memoryLimit, updated.resources.memoryLimit)
    assertEquals(original.resources.storageSize, updated.resources.storageSize)
  }

  @Test
  fun `updateWorkbench persists changes to database`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "persist-wb"))

    service.updateWorkbench(
      "user-1", "persist-wb",
      WorkbenchUpdateRequest(image = "theiaide/theia:updated", resources = ResourceSpec(cpuRequest = "8"))
    )

    val entity = repository.findById(created.instanceId).get()
    assertEquals("theiaide/theia:updated", entity.image)
    assertEquals("8", entity.cpuRequest)
  }

  @Test
  fun `updateWorkbench reapplies K8s deployment and service`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "k8s-upd-wb"))

    service.updateWorkbench(
      "user-1", "k8s-upd-wb",
      WorkbenchUpdateRequest(image = "theiaide/theia:v2")
    )

    // Verify deployment still exists after update
    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", created.instanceId.toString()).list().items
    assertEquals(1, deployments.size)

    // Verify service still exists after update
    val services = client.services().inNamespace("default")
      .withLabel("instance-id", created.instanceId.toString()).list().items
    assertEquals(1, services.size)
  }

  // ── suspendWorkbench ────────────────────────────────────────────────

  @Test
  fun `suspendWorkbench sets status to SUSPENDED and removes deployment and service but keeps PVC`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "suspend-wb"))
    val instanceId = created.instanceId.toString()

    // Verify all 3 resources exist before suspend
    assertEquals(1, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(1, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(1, client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)

    val response = service.suspendWorkbench("user-1", "suspend-wb")

    assertEquals(WorkbenchStatus.SUSPENDED.name, response.status)
    assertEquals("suspend-wb", response.instanceName)

    // Deployment and Service should be deleted
    assertEquals(0, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(0, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)

    // PVC should still exist
    assertEquals(1, client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
  }

  @Test
  fun `suspendWorkbench persists SUSPENDED status in database`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "suspend-persist-wb"))

    service.suspendWorkbench("user-1", "suspend-persist-wb")

    val entity = repository.findById(created.instanceId).get()
    assertEquals(WorkbenchStatus.SUSPENDED.name, entity.status)
  }

  @Test
  fun `suspendWorkbench throws WorkbenchNotFoundException for missing workbench`() {
    assertThrows<WorkbenchNotFoundException> {
      service.suspendWorkbench("user-1", "nonexistent")
    }
  }

  @Test
  fun `suspendWorkbench throws IllegalArgumentException when already suspended`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "already-suspended-wb"))
    service.suspendWorkbench("user-1", "already-suspended-wb")

    val exception = assertThrows<IllegalArgumentException> {
      service.suspendWorkbench("user-1", "already-suspended-wb")
    }
    assertTrue(exception.message!!.contains("already suspended"))
  }

  @Test
  fun `suspended workbenches still appear in list queries`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "active-wb"))
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "to-suspend-wb"))

    service.suspendWorkbench("user-1", "to-suspend-wb")

    val workbenches = service.listWorkbenches("user-1")
    assertEquals(2, workbenches.size)

    val suspendedWb = workbenches.first { it.instanceName == "to-suspend-wb" }
    assertEquals(WorkbenchStatus.SUSPENDED.name, suspendedWb.status)
  }

  // ── resumeWorkbench ─────────────────────────────────────────────────

  @Test
  fun `resumeWorkbench re-creates deployment and service and sets status to RUNNING`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "resume-wb"))
    val instanceId = created.instanceId.toString()

    service.suspendWorkbench("user-1", "resume-wb")

    // Verify deployment and service are gone after suspend
    assertEquals(0, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(0, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)

    val response = service.resumeWorkbench("user-1", "resume-wb")

    assertEquals(WorkbenchStatus.RUNNING.name, response.status)
    assertEquals("resume-wb", response.instanceName)

    // Deployment and Service should be re-created
    assertEquals(1, client.apps().deployments().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
    assertEquals(1, client.services().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)

    // PVC should still exist
    assertEquals(1, client.persistentVolumeClaims().inNamespace("default")
      .withLabel("instance-id", instanceId).list().items.size)
  }

  @Test
  fun `resumeWorkbench persists RUNNING status in database`() {
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "resume-persist-wb"))
    service.suspendWorkbench("user-1", "resume-persist-wb")

    service.resumeWorkbench("user-1", "resume-persist-wb")

    val entity = repository.findById(created.instanceId).get()
    assertEquals(WorkbenchStatus.RUNNING.name, entity.status)
  }

  @Test
  fun `resumeWorkbench throws WorkbenchNotFoundException for missing workbench`() {
    assertThrows<WorkbenchNotFoundException> {
      service.resumeWorkbench("user-1", "nonexistent")
    }
  }

  @Test
  fun `resumeWorkbench throws IllegalArgumentException when workbench is not suspended`() {
    service.createWorkbench("user-1", WorkbenchCreateRequest(instanceName = "running-wb"))

    val exception = assertThrows<IllegalArgumentException> {
      service.resumeWorkbench("user-1", "running-wb")
    }
    assertTrue(exception.message!!.contains("is not suspended"))
  }

  @Test
  fun `full suspend and resume cycle preserves workbench data`() {
    val customResources = ResourceSpec(
      cpuRequest = "2",
      cpuLimit = "4",
      memoryRequest = "2Gi",
      memoryLimit = "4Gi",
      storageSize = "20Gi"
    )
    val created = service.createWorkbench("user-1", WorkbenchCreateRequest(
      instanceName = "cycle-wb",
      image = "theiaide/theia:custom",
      resources = customResources
    ))

    service.suspendWorkbench("user-1", "cycle-wb")
    val resumed = service.resumeWorkbench("user-1", "cycle-wb")

    assertEquals(created.instanceId, resumed.instanceId)
    assertEquals("theiaide/theia:custom", resumed.image)
    assertEquals("2", resumed.resources.cpuRequest)
    assertEquals("4", resumed.resources.cpuLimit)
    assertEquals("2Gi", resumed.resources.memoryRequest)
    assertEquals("4Gi", resumed.resources.memoryLimit)
    assertEquals("20Gi", resumed.resources.storageSize)
    assertEquals(WorkbenchStatus.RUNNING.name, resumed.status)
  }
}
