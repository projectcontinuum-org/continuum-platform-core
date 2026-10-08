package org.projectcontinuum.core.cluster.manager.repository

import org.projectcontinuum.core.cluster.manager.entity.FeatureWorkerInstanceEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface FeatureWorkerInstanceRepository : CrudRepository<FeatureWorkerInstanceEntity, UUID> {

  @Query("SELECT * FROM feature_worker_instances WHERE namespace = :namespace AND worker_name = :workerName AND status NOT IN ('DELETED', 'TERMINATING') LIMIT 1")
  fun findByNamespaceAndWorkerName(namespace: String, workerName: String): FeatureWorkerInstanceEntity?

  @Query("SELECT * FROM feature_worker_instances WHERE namespace = :namespace AND status NOT IN ('DELETED', 'TERMINATING')")
  fun findByNamespace(namespace: String): List<FeatureWorkerInstanceEntity>

  @Query("SELECT * FROM feature_worker_instances WHERE task_queue = :taskQueue AND status NOT IN ('DELETED', 'TERMINATING') LIMIT 1")
  fun findByTaskQueue(taskQueue: String): FeatureWorkerInstanceEntity?
}
