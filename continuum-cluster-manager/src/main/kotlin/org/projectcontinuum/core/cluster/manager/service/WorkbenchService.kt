package org.projectcontinuum.core.cluster.manager.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import freemarker.template.Configuration
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties
import org.projectcontinuum.core.cluster.manager.entity.WorkbenchInstanceEntity
import org.projectcontinuum.core.cluster.manager.exception.WorkbenchNotFoundException
import org.projectcontinuum.core.cluster.manager.model.*
import org.projectcontinuum.core.cluster.manager.repository.WorkbenchInstanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.StringWriter
import java.time.Instant
import java.util.UUID

@Service
class WorkbenchService(
  private val repository: WorkbenchInstanceRepository,
  private val kubernetesClient: KubernetesClient,
  private val freemarkerConfig: Configuration,
  private val transactionTemplate: TransactionTemplate,
  private val workbenchProperties: WorkbenchProperties
) {

  private val logger = LoggerFactory.getLogger(WorkbenchService::class.java)
  private val objectMapper = jacksonObjectMapper()

  /**
   * Audit operation types for workbench lifecycle events
   */
  private enum class AuditOperation {
    CREATE, DELETE, SUSPEND, RESUME, UPDATE, GET_STATUS, LIST
  }

  /**
   * Logs an audit event for workbench operations.
   * Format: AUDIT | operation=X | userId=Y | instanceId=Z | instanceName=W | status=S | details={...}
   */
  private fun logAudit(
    operation: AuditOperation,
    userId: String,
    instanceId: UUID? = null,
    instanceName: String? = null,
    status: String = "SUCCESS",
    details: Map<String, Any?> = emptyMap()
  ) {
    val detailsJson = if (details.isNotEmpty()) objectMapper.writeValueAsString(details) else "{}"
    logger.info(
      "AUDIT | operation={} | userId={} | instanceId={} | instanceName={} | status={} | details={}",
      operation.name,
      userId,
      instanceId?.toString() ?: "N/A",
      instanceName ?: "N/A",
      status,
      detailsJson
    )
  }

  fun createWorkbench(userId: String, request: WorkbenchCreateRequest): WorkbenchResponse {
    logger.info("AUDIT | operation=CREATE | userId={} | instanceName={} | status=INITIATED", userId, request.instanceName)

    val existing = repository.findByUserIdAndInstanceName(userId, request.instanceName)
    val activeStatuses = setOf(WorkbenchStatus.RUNNING.name, WorkbenchStatus.SUSPENDED.name)
    if (existing != null && existing.status in activeStatuses) {
      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        instanceName = request.instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Workbench already exists")
      )
      throw IllegalArgumentException("Workbench '${request.instanceName}' already exists for user '$userId'")
    }

    val instanceId = UUID.randomUUID()
    val now = Instant.now()
    val namespace = workbenchProperties.namespace

    val entity = WorkbenchInstanceEntity(
      instanceId = instanceId,
      instanceName = request.instanceName,
      namespace = namespace,
      userId = userId,
      status = WorkbenchStatus.PENDING.name,
      image = request.image,
      cpuRequest = request.resources.cpuRequest,
      cpuLimit = request.resources.cpuLimit,
      memoryRequest = request.resources.memoryRequest,
      memoryLimit = request.resources.memoryLimit,
      storageSize = request.resources.storageSize,
      storageClassName = request.resources.storageClassName,
      createdAt = now,
      updatedAt = now
    )

    val templateModel = buildTemplateModel(entity)
    val k8sResourceIds = mutableListOf<String>()

    try {
      // First, create all K8s resources
      val pvcYaml = renderTemplate("pvc.ftl", templateModel)
      applyYaml(pvcYaml, namespace)
      k8sResourceIds.add("persistentvolumeclaim/wb-${instanceId}-pvc")

      val deploymentYaml = renderTemplate("deployment.ftl", templateModel)
      applyYaml(deploymentYaml, namespace)
      k8sResourceIds.add("deployment/wb-${instanceId}-deployment")

      val serviceYaml = renderTemplate("service.ftl", templateModel)
      applyYaml(serviceYaml, namespace)
      k8sResourceIds.add("service/wb-${instanceId}-svc")

      // Only save to DB after all K8s resources are successfully created
      val savedEntity = transactionTemplate.execute {
        val entityToSave = entity.copy(
          status = WorkbenchStatus.RUNNING.name,
          k8sResources = objectMapper.writeValueAsString(k8sResourceIds),
          updatedAt = Instant.now()
        )
        repository.save(entityToSave)
      }!!

      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        instanceId = instanceId,
        instanceName = request.instanceName,
        status = "SUCCESS",
        details = mapOf(
          "namespace" to namespace,
          "image" to request.image,
          "cpuRequest" to request.resources.cpuRequest,
          "memoryRequest" to request.resources.memoryRequest,
          "storageSize" to request.resources.storageSize
        )
      )

      return toResponse(savedEntity)
    } catch (ex: Exception) {
      logger.error("Failed to create K8s resources for workbench $instanceId, rolling back", ex)
      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        instanceId = instanceId,
        instanceName = request.instanceName,
        status = "FAILED",
        details = mapOf("reason" to (ex.message ?: "Unknown error"))
      )
      // Rollback K8s resources that were created
      rollbackK8sResources(k8sResourceIds, namespace)
      throw ex
    }
  }

  fun getWorkbenchStatus(userId: String, instanceName: String): WorkbenchResponse {
    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: run {
        logAudit(
          operation = AuditOperation.GET_STATUS,
          userId = userId,
          instanceName = instanceName,
          status = "FAILED",
          details = mapOf("reason" to "Workbench not found")
        )
        throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")
      }

    val refreshedEntity = refreshStatusFromK8s(entity)

    logAudit(
      operation = AuditOperation.GET_STATUS,
      userId = userId,
      instanceId = entity.instanceId,
      instanceName = instanceName,
      status = "SUCCESS",
      details = mapOf("workbenchStatus" to refreshedEntity.status)
    )

    return toResponse(refreshedEntity)
  }

  fun deleteWorkbench(userId: String, instanceName: String) {
    logger.info("AUDIT | operation=DELETE | userId={} | instanceName={} | status=INITIATED", userId, instanceName)

    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: run {
        logAudit(
          operation = AuditOperation.DELETE,
          userId = userId,
          instanceName = instanceName,
          status = "FAILED",
          details = mapOf("reason" to "Workbench not found")
        )
        throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")
      }

    // First, delete K8s resources
    try {
      deleteK8sResourcesByLabel(entity.instanceId.toString(), entity.namespace)
    } catch (ex: Exception) {
      logger.error("Failed to delete K8s resources for workbench ${entity.instanceId}", ex)
      logAudit(
        operation = AuditOperation.DELETE,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to delete K8s resources: ${ex.message}")
      )
      throw ex
    }

    // Only update DB after K8s resources are successfully deleted
    transactionTemplate.execute {
      val deletedEntity = entity.copy(
        status = WorkbenchStatus.DELETED.name,
        updatedAt = Instant.now()
      )
      repository.save(deletedEntity)
    }

    logAudit(
      operation = AuditOperation.DELETE,
      userId = userId,
      instanceId = entity.instanceId,
      instanceName = instanceName,
      status = "SUCCESS",
      details = mapOf("previousStatus" to entity.status)
    )
  }

  @Transactional(readOnly = true)
  fun listWorkbenches(userId: String): List<WorkbenchResponse> {
    val entities = repository.findByUserId(userId)

    logAudit(
      operation = AuditOperation.LIST,
      userId = userId,
      status = "SUCCESS",
      details = mapOf("count" to entities.size)
    )

    return entities.map { toResponse(it) }
  }

  fun suspendWorkbench(userId: String, instanceName: String): WorkbenchResponse {
    logger.info("AUDIT | operation=SUSPEND | userId={} | instanceName={} | status=INITIATED", userId, instanceName)

    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: run {
        logAudit(
          operation = AuditOperation.SUSPEND,
          userId = userId,
          instanceName = instanceName,
          status = "FAILED",
          details = mapOf("reason" to "Workbench not found")
        )
        throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")
      }

    if (entity.status == WorkbenchStatus.SUSPENDED.name) {
      logAudit(
        operation = AuditOperation.SUSPEND,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Workbench already suspended")
      )
      throw IllegalArgumentException("Workbench '$instanceName' is already suspended")
    }

    // First, suspend K8s resources (delete deployment and service, keep PVC)
    try {
      suspendK8sResources(entity.instanceId.toString(), entity.namespace)
    } catch (ex: Exception) {
      logger.error("Failed to suspend K8s resources for workbench ${entity.instanceId}", ex)
      logAudit(
        operation = AuditOperation.SUSPEND,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to suspend K8s resources: ${ex.message}")
      )
      throw ex
    }

    // Only update DB after K8s resources are successfully suspended
    val suspendedEntity = transactionTemplate.execute {
      val entityToSave = entity.copy(
        status = WorkbenchStatus.SUSPENDED.name,
        updatedAt = Instant.now()
      )
      repository.save(entityToSave)
    }!!

    logAudit(
      operation = AuditOperation.SUSPEND,
      userId = userId,
      instanceId = entity.instanceId,
      instanceName = instanceName,
      status = "SUCCESS",
      details = mapOf("previousStatus" to entity.status)
    )

    return toResponse(suspendedEntity)
  }

  fun resumeWorkbench(userId: String, instanceName: String): WorkbenchResponse {
    logger.info("AUDIT | operation=RESUME | userId={} | instanceName={} | status=INITIATED", userId, instanceName)

    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: run {
        logAudit(
          operation = AuditOperation.RESUME,
          userId = userId,
          instanceName = instanceName,
          status = "FAILED",
          details = mapOf("reason" to "Workbench not found")
        )
        throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")
      }

    if (entity.status != WorkbenchStatus.SUSPENDED.name) {
      logAudit(
        operation = AuditOperation.RESUME,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Workbench is not suspended", "currentStatus" to entity.status)
      )
      throw IllegalArgumentException("Workbench '$instanceName' is not suspended")
    }

    val templateModel = buildTemplateModel(entity)

    // First, recreate K8s resources
    try {
      val deploymentYaml = renderTemplate("deployment.ftl", templateModel)
      applyYaml(deploymentYaml, entity.namespace)

      val serviceYaml = renderTemplate("service.ftl", templateModel)
      applyYaml(serviceYaml, entity.namespace)
    } catch (ex: Exception) {
      logger.error("Failed to resume K8s resources for workbench ${entity.instanceId}, rolling back", ex)
      logAudit(
        operation = AuditOperation.RESUME,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to resume K8s resources: ${ex.message}")
      )
      // Rollback any partially created resources
      try {
        suspendK8sResources(entity.instanceId.toString(), entity.namespace)
      } catch (rollbackEx: Exception) {
        logger.error("Failed to rollback K8s resources during resume failure", rollbackEx)
      }
      throw ex
    }

    // Only update DB after K8s resources are successfully created
    val resumedEntity = transactionTemplate.execute {
      val entityToSave = entity.copy(
        status = WorkbenchStatus.RUNNING.name,
        updatedAt = Instant.now()
      )
      repository.save(entityToSave)
    }!!

    logAudit(
      operation = AuditOperation.RESUME,
      userId = userId,
      instanceId = entity.instanceId,
      instanceName = instanceName,
      status = "SUCCESS",
      details = mapOf("previousStatus" to entity.status)
    )

    return toResponse(resumedEntity)
  }

  fun updateWorkbench(userId: String, instanceName: String, request: WorkbenchUpdateRequest): WorkbenchResponse {
    logger.info("AUDIT | operation=UPDATE | userId={} | instanceName={} | status=INITIATED", userId, instanceName)

    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
      ?: run {
        logAudit(
          operation = AuditOperation.UPDATE,
          userId = userId,
          instanceName = instanceName,
          status = "FAILED",
          details = mapOf("reason" to "Workbench not found")
        )
        throw WorkbenchNotFoundException("Workbench '$instanceName' not found for user '$userId'")
      }

    val updatedEntity = entity.copy(
      image = request.image ?: entity.image,
      cpuRequest = request.resources?.cpuRequest ?: entity.cpuRequest,
      cpuLimit = request.resources?.cpuLimit ?: entity.cpuLimit,
      memoryRequest = request.resources?.memoryRequest ?: entity.memoryRequest,
      memoryLimit = request.resources?.memoryLimit ?: entity.memoryLimit,
      storageSize = request.resources?.storageSize ?: entity.storageSize,
      storageClassName = request.resources?.storageClassName ?: entity.storageClassName,
      updatedAt = Instant.now()
    )

    // Build change details for audit
    val changes = buildMap<String, Any> {
      request.image?.let { if (it != entity.image) put("image", mapOf("from" to entity.image, "to" to it)) }
      request.resources?.cpuRequest?.let { if (it != entity.cpuRequest) put("cpuRequest", mapOf("from" to entity.cpuRequest, "to" to it)) }
      request.resources?.cpuLimit?.let { if (it != entity.cpuLimit) put("cpuLimit", mapOf("from" to entity.cpuLimit, "to" to it)) }
      request.resources?.memoryRequest?.let { if (it != entity.memoryRequest) put("memoryRequest", mapOf("from" to entity.memoryRequest, "to" to it)) }
      request.resources?.memoryLimit?.let { if (it != entity.memoryLimit) put("memoryLimit", mapOf("from" to entity.memoryLimit, "to" to it)) }
      request.resources?.storageSize?.let { if (it != entity.storageSize) put("storageSize", mapOf("from" to entity.storageSize, "to" to it)) }
    }

    val templateModel = buildTemplateModel(updatedEntity)

    // First, update K8s resources
    try {
      val deploymentYaml = renderTemplate("deployment.ftl", templateModel)
      applyYaml(deploymentYaml, updatedEntity.namespace)

      val serviceYaml = renderTemplate("service.ftl", templateModel)
      applyYaml(serviceYaml, updatedEntity.namespace)
    } catch (ex: Exception) {
      logger.error("Failed to update K8s resources for workbench ${entity.instanceId}, rolling back", ex)
      logAudit(
        operation = AuditOperation.UPDATE,
        userId = userId,
        instanceId = entity.instanceId,
        instanceName = instanceName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to update K8s resources: ${ex.message}")
      )
      // Rollback by reapplying old configuration
      try {
        val oldTemplateModel = buildTemplateModel(entity)
        val oldDeploymentYaml = renderTemplate("deployment.ftl", oldTemplateModel)
        applyYaml(oldDeploymentYaml, entity.namespace)
        val oldServiceYaml = renderTemplate("service.ftl", oldTemplateModel)
        applyYaml(oldServiceYaml, entity.namespace)
      } catch (rollbackEx: Exception) {
        logger.error("Failed to rollback K8s resources during update failure", rollbackEx)
      }
      throw ex
    }

    // Only save to DB after K8s resources are successfully updated
    val savedEntity = transactionTemplate.execute {
      repository.save(updatedEntity)
    }!!

    logAudit(
      operation = AuditOperation.UPDATE,
      userId = userId,
      instanceId = entity.instanceId,
      instanceName = instanceName,
      status = "SUCCESS",
      details = if (changes.isNotEmpty()) mapOf("changes" to changes) else emptyMap()
    )

    return toResponse(savedEntity)
  }

  private fun refreshStatusFromK8s(entity: WorkbenchInstanceEntity): WorkbenchInstanceEntity {
    return try {
      val deployment = kubernetesClient.apps().deployments()
        .inNamespace(entity.namespace)
        .withName("wb-${entity.instanceId}-deployment")
        .get()

      val newStatus = if (deployment == null) {
        WorkbenchStatus.UNKNOWN.name
      } else {
        val readyReplicas = deployment.status?.readyReplicas ?: 0
        if (readyReplicas > 0) WorkbenchStatus.RUNNING.name else WorkbenchStatus.PENDING.name
      }

      if (newStatus != entity.status) {
        val updated = entity.copy(status = newStatus, updatedAt = Instant.now())
        repository.save(updated)
        updated
      } else {
        entity
      }
    } catch (ex: Exception) {
      logger.warn("Could not refresh K8s status for workbench ${entity.instanceId}", ex)
      entity
    }
  }

  private fun rollbackK8sResources(resourceIds: List<String>, namespace: String) {
    for (resourceId in resourceIds.reversed()) {
      try {
        val parts = resourceId.split("/")
        if (parts.size != 2) continue
        val (kind, name) = parts
        when (kind) {
          "persistentvolumeclaim" -> kubernetesClient.persistentVolumeClaims()
            .inNamespace(namespace).withName(name).delete()
          "deployment" -> kubernetesClient.apps().deployments()
            .inNamespace(namespace).withName(name).delete()
          "service" -> kubernetesClient.services()
            .inNamespace(namespace).withName(name).delete()
        }
        logger.info("Rolled back K8s resource: $resourceId")
      } catch (ex: Exception) {
        logger.warn("Failed to rollback K8s resource: $resourceId", ex)
      }
    }
  }

  private fun deleteK8sResourcesByLabel(instanceId: String, namespace: String) {
    val labels = mapOf(
      "instance-id" to instanceId,
      "app" to "continuum-workbench",
      "managed-by" to "continuum-cluster-manager"
    )

    kubernetesClient.apps().deployments()
      .inNamespace(namespace).withLabels(labels).delete()
    kubernetesClient.services()
      .inNamespace(namespace).withLabels(labels).delete()
    kubernetesClient.persistentVolumeClaims()
      .inNamespace(namespace).withLabels(labels).delete()
  }

  private fun suspendK8sResources(instanceId: String, namespace: String) {
    val labels = mapOf(
      "instance-id" to instanceId,
      "app" to "continuum-workbench",
      "managed-by" to "continuum-cluster-manager"
    )

    kubernetesClient.apps().deployments()
      .inNamespace(namespace).withLabels(labels).delete()
    kubernetesClient.services()
      .inNamespace(namespace).withLabels(labels).delete()
  }

  private fun renderTemplate(templateName: String, model: Map<String, Any?>): String {
    val template = freemarkerConfig.getTemplate(templateName)
    val writer = StringWriter()
    template.process(model, writer)
    return writer.toString()
  }

  @Suppress("DEPRECATION")
  private fun applyYaml(yaml: String, namespace: String) {
    val resources: List<HasMetadata> = kubernetesClient.load(yaml.byteInputStream()).items()
    for (resource in resources) {
      kubernetesClient.resource(resource)
        .inNamespace(namespace)
        .createOrReplace()
    }
  }

  private fun buildTemplateModel(entity: WorkbenchInstanceEntity): Map<String, Any?> {
    return mapOf(
      "instanceId" to entity.instanceId.toString(),
      "namespace" to entity.namespace,
      "image" to entity.image,
      "cpuRequest" to entity.cpuRequest,
      "cpuLimit" to entity.cpuLimit,
      "memoryRequest" to entity.memoryRequest,
      "memoryLimit" to entity.memoryLimit,
      "storageSize" to entity.storageSize,
      "storageClassName" to (entity.storageClassName ?: "")
    )
  }

  private fun toResponse(entity: WorkbenchInstanceEntity): WorkbenchResponse {
    return WorkbenchResponse(
      instanceId = entity.instanceId,
      instanceName = entity.instanceName,
      namespace = entity.namespace,
      userId = entity.userId,
      status = entity.status,
      image = entity.image,
      resources = ResourceSpec(
        cpuRequest = entity.cpuRequest,
        cpuLimit = entity.cpuLimit,
        memoryRequest = entity.memoryRequest,
        memoryLimit = entity.memoryLimit,
        storageSize = entity.storageSize,
        storageClassName = entity.storageClassName
      ),
      serviceEndpoint = "wb-${entity.instanceId}-svc.${entity.namespace}.svc.cluster.local:8080",
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  }
}
