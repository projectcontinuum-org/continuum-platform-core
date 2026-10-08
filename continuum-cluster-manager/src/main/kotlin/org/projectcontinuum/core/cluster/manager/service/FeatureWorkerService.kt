package org.projectcontinuum.core.cluster.manager.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import freemarker.template.Configuration
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import org.projectcontinuum.core.cluster.manager.config.FeatureWorkerProperties
import org.projectcontinuum.core.cluster.manager.entity.FeatureWorkerInstanceEntity
import org.projectcontinuum.core.cluster.manager.exception.FeatureWorkerNotFoundException
import org.projectcontinuum.core.cluster.manager.model.*
import org.projectcontinuum.core.cluster.manager.repository.FeatureWorkerInstanceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.StringWriter
import java.time.Instant
import java.util.UUID

@Service
class FeatureWorkerService(
  private val repository: FeatureWorkerInstanceRepository,
  private val kubernetesClient: KubernetesClient,
  private val freemarkerConfig: Configuration,
  private val transactionTemplate: TransactionTemplate,
  private val featureWorkerProperties: FeatureWorkerProperties,
  @Qualifier("featureWorkerOverlayService") private val overlayService: OverlayService
) {

  private val logger = LoggerFactory.getLogger(FeatureWorkerService::class.java)
  private val objectMapper = jacksonObjectMapper()

  private val taskQueuePattern = Regex("^CONTINUUM-FEATURE-[A-Z0-9-]+-TASK-QUEUE$")

  /**
   * Audit operation types for feature worker lifecycle events
   */
  private enum class AuditOperation {
    CREATE, DELETE, UPDATE, GET_STATUS, LIST
  }

  /**
   * Logs an audit event for feature worker operations.
   * Format: AUDIT | operation=X | userId=Y | workerId=Z | workerName=W | status=S | details={...}
   */
  private fun logAudit(
    operation: AuditOperation,
    userId: String,
    workerId: UUID? = null,
    workerName: String? = null,
    status: String = "SUCCESS",
    details: Map<String, Any?> = emptyMap()
  ) {
    val detailsJson = if (details.isNotEmpty()) objectMapper.writeValueAsString(details) else "{}"
    logger.info(
      "AUDIT | operation={} | userId={} | workerId={} | workerName={} | status={} | details={}",
      operation.name,
      userId,
      workerId?.toString() ?: "N/A",
      workerName ?: "N/A",
      status,
      detailsJson
    )
  }

  /**
   * Validates a task queue name: it must match CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE
   * and must not already be in use by a different active feature worker.
   *
   * @param excludeWorkerId when updating an existing worker, excludes that worker's own
   *   row from the uniqueness check so a no-op update doesn't self-conflict.
   */
  private fun validateTaskQueue(taskQueue: String, excludeWorkerId: UUID? = null) {
    if (!taskQueuePattern.matches(taskQueue)) {
      throw IllegalArgumentException(
        "Task queue '$taskQueue' is invalid. It must match the pattern CONTINUUM-FEATURE-{feature-name}-TASK-QUEUE"
      )
    }

    val existing = repository.findByTaskQueue(taskQueue)
    if (existing != null && existing.workerId != excludeWorkerId) {
      throw IllegalArgumentException(
        "Task queue '$taskQueue' is already in use by feature worker '${existing.workerName}'"
      )
    }
  }

  fun createFeatureWorker(userId: String, request: FeatureWorkerCreateRequest): FeatureWorkerResponse {
    logger.info("AUDIT | operation=CREATE | userId={} | workerName={} | status=INITIATED", userId, request.workerName)

    validateTaskQueue(request.taskQueue)

    val namespace = featureWorkerProperties.namespace
    val existing = repository.findByNamespaceAndWorkerName(namespace, request.workerName)
    if (existing != null) {
      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        workerName = request.workerName,
        status = "FAILED",
        details = mapOf("reason" to "Feature worker already exists")
      )
      throw IllegalArgumentException("Feature worker '${request.workerName}' already exists in namespace '$namespace'")
    }

    val workerId = UUID.randomUUID()
    val now = Instant.now()
    val resources = request.resolvedResources()
    val autoscaling = request.resolvedAutoscaling()

    val entity = FeatureWorkerInstanceEntity(
      workerId = workerId,
      workerName = request.workerName,
      namespace = namespace,
      createdBy = userId,
      status = FeatureWorkerStatus.PENDING.name,
      image = request.image,
      taskQueue = request.taskQueue,
      replicas = request.replicas,
      autoscalingEnabled = autoscaling.enabled,
      autoscalingMinReplicas = autoscaling.minReplicas,
      autoscalingMaxReplicas = autoscaling.maxReplicas,
      autoscalingTargetCpuPercent = autoscaling.targetCPUUtilizationPercentage,
      cpuRequest = resources.cpuRequest,
      cpuLimit = resources.cpuLimit,
      memoryRequest = resources.memoryRequest,
      memoryLimit = resources.memoryLimit,
      pvcEnabled = request.pvcEnabled,
      storageSize = resources.storageSize,
      storageClassName = resources.storageClassName,
      envVars = objectMapper.writeValueAsString(request.envVars),
      overlayVariant = request.variant,
      createdAt = now,
      updatedAt = now
    )

    val templateModel = buildTemplateModel(entity)
    val variant = entity.overlayVariant
    val k8sResourceIds = mutableListOf<String>()

    try {
      renderAndApply("feature-worker-deployment.ftl", templateModel, ResourceType.DEPLOYMENT, namespace, variant)
      k8sResourceIds.add("deployment/fw-${workerId}-deployment")

      if (request.pvcEnabled) {
        renderAndApply("feature-worker-pvc.ftl", templateModel, ResourceType.PVC, namespace, variant)
        k8sResourceIds.add("persistentvolumeclaim/fw-${workerId}-pvc")
      }

      if (autoscaling.enabled) {
        renderAndApply("feature-worker-hpa.ftl", templateModel, ResourceType.HPA, namespace, variant)
        k8sResourceIds.add("hpa/fw-${workerId}-hpa")
      }

      val savedEntity = transactionTemplate.execute {
        val entityToSave = entity.copy(
          status = FeatureWorkerStatus.RUNNING.name,
          k8sResources = objectMapper.writeValueAsString(k8sResourceIds),
          updatedAt = Instant.now()
        )
        repository.save(entityToSave)
      }!!

      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        workerId = workerId,
        workerName = request.workerName,
        status = "SUCCESS",
        details = mapOf(
          "namespace" to namespace,
          "image" to request.image,
          "taskQueue" to request.taskQueue,
          "replicas" to request.replicas,
          "autoscalingEnabled" to autoscaling.enabled,
          "pvcEnabled" to request.pvcEnabled,
          "overlayVariant" to variant
        )
      )

      return toResponse(savedEntity)
    } catch (ex: Exception) {
      logger.error("Failed to create K8s resources for feature worker $workerId, rolling back", ex)
      logAudit(
        operation = AuditOperation.CREATE,
        userId = userId,
        workerId = workerId,
        workerName = request.workerName,
        status = "FAILED",
        details = mapOf("reason" to (ex.message ?: "Unknown error"))
      )
      rollbackK8sResources(k8sResourceIds, namespace)
      throw ex
    }
  }

  fun getFeatureWorkerStatus(userId: String, workerName: String): FeatureWorkerResponse {
    val namespace = featureWorkerProperties.namespace
    val entity = repository.findByNamespaceAndWorkerName(namespace, workerName)
      ?: run {
        logAudit(
          operation = AuditOperation.GET_STATUS,
          userId = userId,
          workerName = workerName,
          status = "FAILED",
          details = mapOf("reason" to "Feature worker not found")
        )
        throw FeatureWorkerNotFoundException("Feature worker '$workerName' not found")
      }

    val refreshedEntity = refreshStatusFromK8s(entity)

    logAudit(
      operation = AuditOperation.GET_STATUS,
      userId = userId,
      workerId = entity.workerId,
      workerName = workerName,
      status = "SUCCESS",
      details = mapOf("workerStatus" to refreshedEntity.status)
    )

    return toResponse(refreshedEntity)
  }

  fun deleteFeatureWorker(userId: String, workerName: String) {
    logger.info("AUDIT | operation=DELETE | userId={} | workerName={} | status=INITIATED", userId, workerName)

    val namespace = featureWorkerProperties.namespace
    val entity = repository.findByNamespaceAndWorkerName(namespace, workerName)
      ?: run {
        logAudit(
          operation = AuditOperation.DELETE,
          userId = userId,
          workerName = workerName,
          status = "FAILED",
          details = mapOf("reason" to "Feature worker not found")
        )
        throw FeatureWorkerNotFoundException("Feature worker '$workerName' not found")
      }

    try {
      deleteK8sResourcesByLabel(entity.workerId.toString(), entity.namespace)
    } catch (ex: Exception) {
      logger.error("Failed to delete K8s resources for feature worker ${entity.workerId}", ex)
      logAudit(
        operation = AuditOperation.DELETE,
        userId = userId,
        workerId = entity.workerId,
        workerName = workerName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to delete K8s resources: ${ex.message}")
      )
      throw ex
    }

    transactionTemplate.execute {
      val deletedEntity = entity.copy(
        status = FeatureWorkerStatus.DELETED.name,
        updatedAt = Instant.now()
      )
      repository.save(deletedEntity)
    }

    logAudit(
      operation = AuditOperation.DELETE,
      userId = userId,
      workerId = entity.workerId,
      workerName = workerName,
      status = "SUCCESS",
      details = mapOf("previousStatus" to entity.status)
    )
  }

  @Transactional(readOnly = true)
  fun listFeatureWorkers(userId: String): List<FeatureWorkerResponse> {
    val namespace = featureWorkerProperties.namespace
    val entities = repository.findByNamespace(namespace)

    logAudit(
      operation = AuditOperation.LIST,
      userId = userId,
      status = "SUCCESS",
      details = mapOf("count" to entities.size)
    )

    return entities.map { toResponse(it) }
  }

  fun updateFeatureWorker(userId: String, workerName: String, request: FeatureWorkerUpdateRequest): FeatureWorkerResponse {
    logger.info("AUDIT | operation=UPDATE | userId={} | workerName={} | status=INITIATED", userId, workerName)

    val namespace = featureWorkerProperties.namespace
    val entity = repository.findByNamespaceAndWorkerName(namespace, workerName)
      ?: run {
        logAudit(
          operation = AuditOperation.UPDATE,
          userId = userId,
          workerName = workerName,
          status = "FAILED",
          details = mapOf("reason" to "Feature worker not found")
        )
        throw FeatureWorkerNotFoundException("Feature worker '$workerName' not found")
      }

    validateTaskQueue(request.taskQueue, excludeWorkerId = entity.workerId)

    val resolvedAutoscalingEnabled = request.autoscaling?.enabled ?: entity.autoscalingEnabled
    val resolvedAutoscalingMin = request.autoscaling?.minReplicas ?: entity.autoscalingMinReplicas
    val resolvedAutoscalingMax = request.autoscaling?.maxReplicas ?: entity.autoscalingMaxReplicas
    val resolvedAutoscalingTargetCpu = request.autoscaling?.targetCPUUtilizationPercentage ?: entity.autoscalingTargetCpuPercent

    val updatedEntity = entity.copy(
      image = request.image ?: entity.image,
      taskQueue = request.taskQueue,
      replicas = request.replicas ?: entity.replicas,
      autoscalingEnabled = resolvedAutoscalingEnabled,
      autoscalingMinReplicas = resolvedAutoscalingMin,
      autoscalingMaxReplicas = resolvedAutoscalingMax,
      autoscalingTargetCpuPercent = resolvedAutoscalingTargetCpu,
      cpuRequest = request.resources?.cpuRequest ?: entity.cpuRequest,
      cpuLimit = request.resources?.cpuLimit ?: entity.cpuLimit,
      memoryRequest = request.resources?.memoryRequest ?: entity.memoryRequest,
      memoryLimit = request.resources?.memoryLimit ?: entity.memoryLimit,
      pvcEnabled = request.pvcEnabled ?: entity.pvcEnabled,
      storageSize = request.resources?.storageSize ?: entity.storageSize,
      storageClassName = request.resources?.storageClassName ?: entity.storageClassName,
      envVars = request.envVars?.let { objectMapper.writeValueAsString(it) } ?: entity.envVars,
      updatedAt = Instant.now()
    )

    val changes = buildMap<String, Any> {
      request.image?.let { if (it != entity.image) put("image", mapOf("from" to entity.image, "to" to it)) }
      if (request.taskQueue != entity.taskQueue) put("taskQueue", mapOf("from" to entity.taskQueue, "to" to request.taskQueue))
      request.replicas?.let { if (it != entity.replicas) put("replicas", mapOf("from" to entity.replicas, "to" to it)) }
    }

    val templateModel = buildTemplateModel(updatedEntity)
    val variant = updatedEntity.overlayVariant
    val k8sResourceIds = mutableListOf<String>()

    try {
      renderAndApply("feature-worker-deployment.ftl", templateModel, ResourceType.DEPLOYMENT, namespace, variant)
      k8sResourceIds.add("deployment/fw-${entity.workerId}-deployment")

      if (updatedEntity.pvcEnabled) {
        renderAndApply("feature-worker-pvc.ftl", templateModel, ResourceType.PVC, namespace, variant)
        k8sResourceIds.add("persistentvolumeclaim/fw-${entity.workerId}-pvc")
      }

      if (resolvedAutoscalingEnabled) {
        renderAndApply("feature-worker-hpa.ftl", templateModel, ResourceType.HPA, namespace, variant)
        k8sResourceIds.add("hpa/fw-${entity.workerId}-hpa")
      } else {
        kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
          .inNamespace(namespace)
          .withName("fw-${entity.workerId}-hpa")
          .delete()
      }
    } catch (ex: Exception) {
      logger.error("Failed to update K8s resources for feature worker ${entity.workerId}, rolling back", ex)
      logAudit(
        operation = AuditOperation.UPDATE,
        userId = userId,
        workerId = entity.workerId,
        workerName = workerName,
        status = "FAILED",
        details = mapOf("reason" to "Failed to update K8s resources: ${ex.message}")
      )
      try {
        val oldTemplateModel = buildTemplateModel(entity)
        val oldVariant = entity.overlayVariant
        renderAndApply("feature-worker-deployment.ftl", oldTemplateModel, ResourceType.DEPLOYMENT, entity.namespace, oldVariant)
      } catch (rollbackEx: Exception) {
        logger.error("Failed to rollback K8s resources during update failure", rollbackEx)
      }
      throw ex
    }

    val savedEntity = transactionTemplate.execute {
      repository.save(updatedEntity)
    }!!

    logAudit(
      operation = AuditOperation.UPDATE,
      userId = userId,
      workerId = entity.workerId,
      workerName = workerName,
      status = "SUCCESS",
      details = if (changes.isNotEmpty()) mapOf("changes" to changes) else emptyMap()
    )

    return toResponse(savedEntity)
  }

  private fun refreshStatusFromK8s(entity: FeatureWorkerInstanceEntity): FeatureWorkerInstanceEntity {
    return try {
      val deployment = kubernetesClient.apps().deployments()
        .inNamespace(entity.namespace)
        .withName("fw-${entity.workerId}-deployment")
        .get()

      val newStatus = if (deployment == null) {
        FeatureWorkerStatus.UNKNOWN.name
      } else {
        val readyReplicas = deployment.status?.readyReplicas ?: 0
        if (readyReplicas > 0) FeatureWorkerStatus.RUNNING.name else FeatureWorkerStatus.PENDING.name
      }

      if (newStatus != entity.status) {
        val updated = entity.copy(status = newStatus, updatedAt = Instant.now())
        repository.save(updated)
        updated
      } else {
        entity
      }
    } catch (ex: Exception) {
      logger.warn("Could not refresh K8s status for feature worker ${entity.workerId}", ex)
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
          "deployment" -> kubernetesClient.apps().deployments()
            .inNamespace(namespace).withName(name).delete()
          "persistentvolumeclaim" -> kubernetesClient.persistentVolumeClaims()
            .inNamespace(namespace).withName(name).delete()
          "hpa" -> kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
            .inNamespace(namespace).withName(name).delete()
        }
        logger.info("Rolled back K8s resource: $resourceId")
      } catch (ex: Exception) {
        logger.warn("Failed to rollback K8s resource: $resourceId", ex)
      }
    }
  }

  private fun deleteK8sResourcesByLabel(workerId: String, namespace: String) {
    val labels = mapOf(
      "worker-id" to workerId,
      "app" to "continuum-feature-worker",
      "managed-by" to "continuum-cluster-manager"
    )

    kubernetesClient.apps().deployments()
      .inNamespace(namespace).withLabels(labels).delete()
    kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
      .inNamespace(namespace).withLabels(labels).delete()
    kubernetesClient.persistentVolumeClaims()
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

  private fun renderAndApply(
    templateName: String,
    model: Map<String, Any?>,
    resourceType: ResourceType,
    namespace: String,
    variant: String? = null
  ): String {
    val yaml = renderTemplate(templateName, model)
    val mergedYaml = overlayService.applyOverlay(yaml, resourceType, model, variant)
    applyYaml(mergedYaml, namespace)
    return mergedYaml
  }

  private fun buildTemplateModel(entity: FeatureWorkerInstanceEntity): Map<String, Any?> {
    val envVarsMap: Map<String, String> = try {
      objectMapper.readValue<Map<String, String>>(entity.envVars)
    } catch (_: Exception) {
      emptyMap()
    }

    return mapOf(
      "workerId" to entity.workerId.toString(),
      "namespace" to entity.namespace,
      "image" to entity.image,
      "imagePullPolicy" to featureWorkerProperties.imagePullPolicy,
      "taskQueue" to entity.taskQueue,
      "replicas" to entity.replicas,
      "autoscalingEnabled" to entity.autoscalingEnabled,
      "autoscalingMinReplicas" to (entity.autoscalingMinReplicas ?: 1),
      "autoscalingMaxReplicas" to (entity.autoscalingMaxReplicas ?: 1),
      "autoscalingTargetCpuPercent" to (entity.autoscalingTargetCpuPercent ?: 80),
      "cpuRequest" to entity.cpuRequest,
      "cpuLimit" to entity.cpuLimit,
      "memoryRequest" to entity.memoryRequest,
      "memoryLimit" to entity.memoryLimit,
      "pvcEnabled" to entity.pvcEnabled,
      "storageSize" to (entity.storageSize ?: ""),
      "storageClassName" to (entity.storageClassName ?: ""),
      "envVars" to envVarsMap
    )
  }

  private fun toResponse(entity: FeatureWorkerInstanceEntity): FeatureWorkerResponse {
    val envVarsMap: Map<String, String> = try {
      objectMapper.readValue<Map<String, String>>(entity.envVars)
    } catch (_: Exception) {
      emptyMap()
    }

    return FeatureWorkerResponse(
      workerId = entity.workerId,
      workerName = entity.workerName,
      namespace = entity.namespace,
      createdBy = entity.createdBy,
      status = entity.status,
      image = entity.image,
      taskQueue = entity.taskQueue,
      replicas = entity.replicas,
      autoscaling = AutoscalingSpec(
        enabled = entity.autoscalingEnabled,
        minReplicas = entity.autoscalingMinReplicas,
        maxReplicas = entity.autoscalingMaxReplicas,
        targetCPUUtilizationPercentage = entity.autoscalingTargetCpuPercent
      ),
      resources = FeatureWorkerResourceSpec(
        cpuRequest = entity.cpuRequest,
        cpuLimit = entity.cpuLimit,
        memoryRequest = entity.memoryRequest,
        memoryLimit = entity.memoryLimit,
        storageSize = entity.storageSize,
        storageClassName = entity.storageClassName
      ),
      pvcEnabled = entity.pvcEnabled,
      envVars = envVarsMap,
      overlayVariant = entity.overlayVariant,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  }
}
