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
import org.junit.jupiter.api.io.TempDir
import org.projectcontinuum.core.cluster.manager.config.FeatureWorkerOverlayProperties
import org.projectcontinuum.core.cluster.manager.config.FeatureWorkerProperties
import org.projectcontinuum.core.cluster.manager.entity.FeatureWorkerInstanceEntity
import org.projectcontinuum.core.cluster.manager.exception.FeatureWorkerNotFoundException
import org.projectcontinuum.core.cluster.manager.model.*
import org.projectcontinuum.core.cluster.manager.repository.FeatureWorkerInstanceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@SpringBootTest
@EnableKubernetesMockClient(crud = true)
@ActiveProfiles("test")
class FeatureWorkerServiceTest {

  @Autowired
  private lateinit var repository: FeatureWorkerInstanceRepository

  @Autowired
  private lateinit var transactionTemplate: TransactionTemplate

  lateinit var client: KubernetesClient
  lateinit var server: KubernetesMockServer

  private lateinit var service: FeatureWorkerService
  private lateinit var featureWorkerProperties: FeatureWorkerProperties
  private lateinit var freemarkerCfg: Configuration

  @BeforeEach
  fun setUp() {
    repository.deleteAll()

    freemarkerCfg = Configuration(Configuration.VERSION_2_3_34)
    freemarkerCfg.setClassLoaderForTemplateLoading(this::class.java.classLoader, "/templates")
    freemarkerCfg.defaultEncoding = "UTF-8"

    featureWorkerProperties = FeatureWorkerProperties(
      namespace = "default",
      imagePullPolicy = "IfNotPresent"
    )

    val overlayService = OverlayService(FeatureWorkerOverlayProperties(enabled = false), freemarkerCfg, listOf("worker-id", "app", "managed-by"))

    service = FeatureWorkerService(repository, client, freemarkerCfg, transactionTemplate, featureWorkerProperties, overlayService)
  }

  private fun createSampleEntity(
    workerName: String = "test-fw",
    namespace: String = "default",
    createdBy: String = "user-1",
    status: String = FeatureWorkerStatus.RUNNING.name,
    taskQueue: String = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
    workerId: UUID = UUID.randomUUID()
  ): FeatureWorkerInstanceEntity {
    val now = Instant.now()
    return FeatureWorkerInstanceEntity(
      workerId = workerId,
      workerName = workerName,
      namespace = namespace,
      createdBy = createdBy,
      status = status,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = taskQueue,
      createdAt = now,
      updatedAt = now
    )
  }

  private fun sampleCreateRequest(
    workerName: String = "test-fw",
    taskQueue: String = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
    image: String = "projectcontinuum/feature-worker:latest"
  ) = FeatureWorkerCreateRequest(workerName = workerName, image = image, taskQueue = taskQueue)

  // ── createFeatureWorker ──────────────────────────────────────────────

  @Test
  fun `createFeatureWorker saves entity and creates K8s deployment`() {
    val response = service.createFeatureWorker("user-1", sampleCreateRequest())

    assertEquals("test-fw", response.workerName)
    assertEquals("user-1", response.createdBy)
    assertEquals("default", response.namespace)
    assertEquals(FeatureWorkerStatus.RUNNING.name, response.status)
    assertNotNull(response.workerId)

    val saved = repository.findByNamespaceAndWorkerName("default", "test-fw")
    assertNotNull(saved)
    assertEquals(response.workerId, saved!!.workerId)

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    assertEquals(1, deployments.size)
  }

  @Test
  fun `createFeatureWorker throws when worker with same name already exists`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(
      workerName = "dup-fw", taskQueue = "CONTINUUM-FEATURE-DUP-1-TASK-QUEUE"
    ))

    val exception = assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(
        workerName = "dup-fw", taskQueue = "CONTINUUM-FEATURE-DUP-2-TASK-QUEUE"
      ))
    }
    assertTrue(exception.message!!.contains("already exists"))
  }

  @Test
  fun `createFeatureWorker does not create K8s resources when name already exists`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(
      workerName = "dup-fw", taskQueue = "CONTINUUM-FEATURE-DUP-1-TASK-QUEUE"
    ))

    assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(
        workerName = "dup-fw", taskQueue = "CONTINUUM-FEATURE-DUP-2-TASK-QUEUE"
      ))
    }

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("app", "continuum-feature-worker").list().items
    assertEquals(1, deployments.size)
  }

  @Test
  fun `createFeatureWorker with pvcEnabled creates PVC`() {
    val response = service.createFeatureWorker("user-1", sampleCreateRequest().copy(pvcEnabled = true))

    val pvcs = client.persistentVolumeClaims().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    assertEquals(1, pvcs.size)
    assertTrue(response.pvcEnabled)
  }

  @Test
  fun `createFeatureWorker without pvcEnabled does not create PVC`() {
    val response = service.createFeatureWorker("user-1", sampleCreateRequest())

    val pvcs = client.persistentVolumeClaims().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    assertEquals(0, pvcs.size)
  }

  @Test
  fun `createFeatureWorker with autoscaling enabled creates HPA`() {
    val request = sampleCreateRequest().copy(
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5, targetCPUUtilizationPercentage = 70)
    )
    val response = service.createFeatureWorker("user-1", request)

    val hpas = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    assertEquals(1, hpas.size)
    assertTrue(response.autoscaling.enabled)
    assertEquals(1, response.autoscaling.minReplicas)
    assertEquals(5, response.autoscaling.maxReplicas)
    assertEquals(70, response.autoscaling.targetCPUUtilizationPercentage)
  }

  @Test
  fun `createFeatureWorker without autoscaling does not create HPA`() {
    val response = service.createFeatureWorker("user-1", sampleCreateRequest())

    val hpas = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    assertEquals(0, hpas.size)
    assertFalse(response.autoscaling.enabled)
  }

  @Test
  fun `createFeatureWorker stores envVars`() {
    val request = sampleCreateRequest().copy(envVars = mapOf("FOO" to "bar", "BAZ" to "qux"))
    val response = service.createFeatureWorker("user-1", request)

    assertEquals("bar", response.envVars["FOO"])
    assertEquals("qux", response.envVars["BAZ"])

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("worker-id", response.workerId.toString()).list().items
    val envNames = deployments[0].spec.template.spec.containers[0].env.map { it.name }
    assertTrue(envNames.contains("FOO"))
    assertTrue(envNames.contains("BAZ"))
    assertTrue(envNames.contains("CONTINUUM_NODE_TASK_QUEUE"))
  }

  @Test
  fun `createFeatureWorker rolls back K8s resources on failure`() {
    // Force the PVC apply call to fail so the already-created Deployment
    // should then be rolled back.
    server.expect().post()
      .withPath("/api/v1/namespaces/default/persistentvolumeclaims")
      .andReturn(500, mapOf("message" to "forced failure"))
      .always()

    val request = sampleCreateRequest().copy(pvcEnabled = true)

    assertThrows<Exception> {
      service.createFeatureWorker("user-1", request)
    }

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("app", "continuum-feature-worker").list().items
    assertEquals(0, deployments.size)

    assertNull(repository.findByNamespaceAndWorkerName("default", "test-fw"))
  }

  // ── task queue validation ────────────────────────────────────────────

  @Test
  fun `createFeatureWorker rejects task queue with invalid format`() {
    val exception = assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(taskQueue = "invalid-queue-name"))
    }
    assertTrue(exception.message!!.contains("invalid"))
  }

  @Test
  fun `createFeatureWorker rejects task queue missing TASK-QUEUE suffix`() {
    assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(taskQueue = "CONTINUUM-FEATURE-TEST"))
    }
  }

  @Test
  fun `createFeatureWorker rejects task queue with lowercase characters`() {
    assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(taskQueue = "CONTINUUM-FEATURE-test-TASK-QUEUE"))
    }
  }

  @Test
  fun `createFeatureWorker accepts valid task queue format`() {
    val response = service.createFeatureWorker("user-1", sampleCreateRequest(taskQueue = "CONTINUUM-FEATURE-MY-FEATURE-TASK-QUEUE"))
    assertEquals("CONTINUUM-FEATURE-MY-FEATURE-TASK-QUEUE", response.taskQueue)
  }

  @Test
  fun `createFeatureWorker rejects duplicate task queue across different worker names`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(
      workerName = "fw-a", taskQueue = "CONTINUUM-FEATURE-SHARED-TASK-QUEUE"
    ))

    val exception = assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(
        workerName = "fw-b", taskQueue = "CONTINUUM-FEATURE-SHARED-TASK-QUEUE"
      ))
    }
    assertTrue(exception.message!!.contains("already in use"))
  }

  @Test
  fun `createFeatureWorker does not create K8s resources when task queue is invalid`() {
    assertThrows<IllegalArgumentException> {
      service.createFeatureWorker("user-1", sampleCreateRequest(taskQueue = "bad-queue"))
    }

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("app", "continuum-feature-worker").list().items
    assertEquals(0, deployments.size)
  }

  // ── getFeatureWorkerStatus ───────────────────────────────────────────

  @Test
  fun `getFeatureWorkerStatus returns response for existing worker`() {
    val entity = createSampleEntity(workerName = "status-fw")
    repository.save(entity)

    val response = service.getFeatureWorkerStatus("user-1", "status-fw")

    assertEquals("status-fw", response.workerName)
  }

  @Test
  fun `getFeatureWorkerStatus throws FeatureWorkerNotFoundException for missing worker`() {
    assertThrows<FeatureWorkerNotFoundException> {
      service.getFeatureWorkerStatus("user-1", "nonexistent")
    }
  }

  @Test
  fun `getFeatureWorkerStatus refreshes status from K8s when deployment has ready replicas`() {
    val workerId = UUID.randomUUID()
    val entity = createSampleEntity(workerName = "refresh-fw", workerId = workerId, status = FeatureWorkerStatus.PENDING.name)
    repository.save(entity)

    val deployment = DeploymentBuilder()
      .withNewMetadata()
      .withName("fw-$workerId-deployment")
      .withNamespace("default")
      .endMetadata()
      .withStatus(DeploymentStatusBuilder().withReadyReplicas(1).build())
      .build()
    client.apps().deployments().inNamespace("default").resource(deployment).create()

    val response = service.getFeatureWorkerStatus("user-1", "refresh-fw")
    assertEquals(FeatureWorkerStatus.RUNNING.name, response.status)
  }

  @Test
  fun `getFeatureWorkerStatus sets PENDING when deployment has zero ready replicas`() {
    val workerId = UUID.randomUUID()
    val entity = createSampleEntity(workerName = "pending-fw", workerId = workerId, status = FeatureWorkerStatus.RUNNING.name)
    repository.save(entity)

    val deployment = DeploymentBuilder()
      .withNewMetadata()
      .withName("fw-$workerId-deployment")
      .withNamespace("default")
      .endMetadata()
      .withStatus(DeploymentStatusBuilder().withReadyReplicas(0).build())
      .build()
    client.apps().deployments().inNamespace("default").resource(deployment).create()

    val response = service.getFeatureWorkerStatus("user-1", "pending-fw")
    assertEquals(FeatureWorkerStatus.PENDING.name, response.status)
  }

  @Test
  fun `getFeatureWorkerStatus sets UNKNOWN when no deployment found`() {
    val entity = createSampleEntity(workerName = "unknown-fw", status = FeatureWorkerStatus.RUNNING.name)
    repository.save(entity)

    val response = service.getFeatureWorkerStatus("user-1", "unknown-fw")
    assertEquals(FeatureWorkerStatus.UNKNOWN.name, response.status)
  }

  // ── listFeatureWorkers ───────────────────────────────────────────────

  @Test
  fun `listFeatureWorkers returns all workers in namespace`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "fw-1", taskQueue = "CONTINUUM-FEATURE-ONE-TASK-QUEUE"))
    service.createFeatureWorker("user-2", sampleCreateRequest(workerName = "fw-2", taskQueue = "CONTINUUM-FEATURE-TWO-TASK-QUEUE"))

    val workers = service.listFeatureWorkers("user-1")
    assertEquals(2, workers.size)
  }

  @Test
  fun `listFeatureWorkers returns empty list when none exist`() {
    assertTrue(service.listFeatureWorkers("user-1").isEmpty())
  }

  @Test
  fun `deleted workers are excluded from list queries`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "keep-fw", taskQueue = "CONTINUUM-FEATURE-KEEP-TASK-QUEUE"))
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "remove-fw", taskQueue = "CONTINUUM-FEATURE-REMOVE-TASK-QUEUE"))

    assertEquals(2, service.listFeatureWorkers("user-1").size)

    service.deleteFeatureWorker("user-1", "remove-fw")

    val remaining = service.listFeatureWorkers("user-1")
    assertEquals(1, remaining.size)
    assertEquals("keep-fw", remaining[0].workerName)
  }

  // ── deleteFeatureWorker ──────────────────────────────────────────────

  @Test
  fun `deleteFeatureWorker soft-deletes DB record and removes K8s resources by label`() {
    val created = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "delete-fw", taskQueue = "CONTINUUM-FEATURE-DEL-TASK-QUEUE").copy(pvcEnabled = true))

    service.deleteFeatureWorker("user-1", "delete-fw")

    assertNull(repository.findByNamespaceAndWorkerName("default", "delete-fw"))

    val dbRecord = repository.findById(created.workerId)
    assertTrue(dbRecord.isPresent)
    assertEquals("DELETED", dbRecord.get().status)

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(0, deployments.size)

    val pvcs = client.persistentVolumeClaims().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(0, pvcs.size)
  }

  @Test
  fun `deleteFeatureWorker removes HPA when autoscaling was enabled`() {
    val created = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "delete-hpa-fw", taskQueue = "CONTINUUM-FEATURE-DELHPA-TASK-QUEUE").copy(
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 3, targetCPUUtilizationPercentage = 80)
    ))

    service.deleteFeatureWorker("user-1", "delete-hpa-fw")

    val hpas = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(0, hpas.size)
  }

  @Test
  fun `deleteFeatureWorker throws FeatureWorkerNotFoundException for missing worker`() {
    assertThrows<FeatureWorkerNotFoundException> {
      service.deleteFeatureWorker("user-1", "nonexistent")
    }
  }

  @Test
  fun `deleteFeatureWorker frees the task queue for reuse`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "reuse-fw", taskQueue = "CONTINUUM-FEATURE-REUSE-TASK-QUEUE"))
    service.deleteFeatureWorker("user-1", "reuse-fw")

    // Should not throw — task queue is free again since the old worker is DELETED
    val response = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "reuse-fw-2", taskQueue = "CONTINUUM-FEATURE-REUSE-TASK-QUEUE"))
    assertEquals("CONTINUUM-FEATURE-REUSE-TASK-QUEUE", response.taskQueue)
  }

  // ── updateFeatureWorker ──────────────────────────────────────────────

  @Test
  fun `updateFeatureWorker updates image and replicas in place`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "update-fw", taskQueue = "CONTINUUM-FEATURE-UPD-TASK-QUEUE"))

    val updated = service.updateFeatureWorker("user-1", "update-fw", FeatureWorkerUpdateRequest(
      image = "projectcontinuum/feature-worker:v2",
      taskQueue = "CONTINUUM-FEATURE-UPD-TASK-QUEUE",
      replicas = 3
    ))

    assertEquals("projectcontinuum/feature-worker:v2", updated.image)
    assertEquals(3, updated.replicas)
  }

  @Test
  fun `updateFeatureWorker reapplies K8s deployment in place without delete`() {
    val created = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "k8s-upd-fw", taskQueue = "CONTINUUM-FEATURE-K8SUPD-TASK-QUEUE"))

    service.updateFeatureWorker("user-1", "k8s-upd-fw", FeatureWorkerUpdateRequest(
      image = "projectcontinuum/feature-worker:v2",
      taskQueue = "CONTINUUM-FEATURE-K8SUPD-TASK-QUEUE"
    ))

    val deployments = client.apps().deployments().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(1, deployments.size)
    assertEquals("projectcontinuum/feature-worker:v2", deployments[0].spec.template.spec.containers[0].image)
  }

  @Test
  fun `updateFeatureWorker throws FeatureWorkerNotFoundException for missing worker`() {
    assertThrows<FeatureWorkerNotFoundException> {
      service.updateFeatureWorker("user-1", "nonexistent", FeatureWorkerUpdateRequest(taskQueue = "CONTINUUM-FEATURE-X-TASK-QUEUE"))
    }
  }

  @Test
  fun `updateFeatureWorker toggling autoscaling on creates HPA`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "toggle-on-fw", taskQueue = "CONTINUUM-FEATURE-TOGON-TASK-QUEUE"))

    val updated = service.updateFeatureWorker("user-1", "toggle-on-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TOGON-TASK-QUEUE",
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 2, maxReplicas = 6, targetCPUUtilizationPercentage = 75)
    ))

    assertTrue(updated.autoscaling.enabled)
    val hpas = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", updated.workerId.toString()).list().items
    assertEquals(1, hpas.size)
  }

  @Test
  fun `updateFeatureWorker toggling autoscaling off deletes HPA`() {
    val created = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "toggle-off-fw", taskQueue = "CONTINUUM-FEATURE-TOGOFF-TASK-QUEUE").copy(
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 4, targetCPUUtilizationPercentage = 60)
    ))

    val hpasBefore = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(1, hpasBefore.size)

    val updated = service.updateFeatureWorker("user-1", "toggle-off-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TOGOFF-TASK-QUEUE",
      autoscaling = AutoscalingSpec(enabled = false, minReplicas = null, maxReplicas = null, targetCPUUtilizationPercentage = null)
    ))

    assertFalse(updated.autoscaling.enabled)
    val hpasAfter = client.autoscaling().v2().horizontalPodAutoscalers().inNamespace("default")
      .withLabel("worker-id", created.workerId.toString()).list().items
    assertEquals(0, hpasAfter.size)
  }

  @Test
  fun `updateFeatureWorker with only taskQueue preserves other fields`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "partial-upd-fw", taskQueue = "CONTINUUM-FEATURE-PARTIAL-TASK-QUEUE").copy(
      image = "projectcontinuum/feature-worker:original"
    ))

    val updated = service.updateFeatureWorker("user-1", "partial-upd-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-PARTIAL-TASK-QUEUE"
    ))

    assertEquals("projectcontinuum/feature-worker:original", updated.image)
  }

  @Test
  fun `updateFeatureWorker persists changes to database`() {
    val created = service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "persist-fw", taskQueue = "CONTINUUM-FEATURE-PERSIST-TASK-QUEUE"))

    service.updateFeatureWorker("user-1", "persist-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-PERSIST-TASK-QUEUE",
      image = "projectcontinuum/feature-worker:updated"
    ))

    val entity = repository.findById(created.workerId).get()
    assertEquals("projectcontinuum/feature-worker:updated", entity.image)
  }

  // ── task queue validation on update ──────────────────────────────────

  @Test
  fun `updateFeatureWorker rejects invalid task queue format`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "fmt-upd-fw", taskQueue = "CONTINUUM-FEATURE-FMTUPD-TASK-QUEUE"))

    assertThrows<IllegalArgumentException> {
      service.updateFeatureWorker("user-1", "fmt-upd-fw", FeatureWorkerUpdateRequest(taskQueue = "not-a-valid-queue"))
    }
  }

  @Test
  fun `updateFeatureWorker rejects task queue already used by a different worker`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "owner-fw", taskQueue = "CONTINUUM-FEATURE-TAKEN-TASK-QUEUE"))
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "other-fw", taskQueue = "CONTINUUM-FEATURE-OTHER-TASK-QUEUE"))

    val exception = assertThrows<IllegalArgumentException> {
      service.updateFeatureWorker("user-1", "other-fw", FeatureWorkerUpdateRequest(taskQueue = "CONTINUUM-FEATURE-TAKEN-TASK-QUEUE"))
    }
    assertTrue(exception.message!!.contains("already in use"))
  }

  @Test
  fun `updateFeatureWorker allows re-submitting its own unchanged task queue`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "self-fw", taskQueue = "CONTINUUM-FEATURE-SELF-TASK-QUEUE"))

    // Should not throw — excludeWorkerId excludes the entity's own id from the uniqueness check
    val updated = service.updateFeatureWorker("user-1", "self-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-SELF-TASK-QUEUE",
      image = "projectcontinuum/feature-worker:v3"
    ))

    assertEquals("CONTINUUM-FEATURE-SELF-TASK-QUEUE", updated.taskQueue)
    assertEquals("projectcontinuum/feature-worker:v3", updated.image)
  }

  @Test
  fun `updateFeatureWorker allows changing to a new unused task queue`() {
    service.createFeatureWorker("user-1", sampleCreateRequest(workerName = "rename-fw", taskQueue = "CONTINUUM-FEATURE-OLDNAME-TASK-QUEUE"))

    val updated = service.updateFeatureWorker("user-1", "rename-fw", FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-NEWNAME-TASK-QUEUE"
    ))

    assertEquals("CONTINUUM-FEATURE-NEWNAME-TASK-QUEUE", updated.taskQueue)
  }
}
