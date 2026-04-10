package org.projectcontinuum.core.cluster.manager.repository

import org.projectcontinuum.core.cluster.manager.entity.WorkbenchInstanceEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface WorkbenchInstanceRepository : CrudRepository<WorkbenchInstanceEntity, UUID> {

  @Query("SELECT * FROM workbench_instances WHERE user_id = :userId AND instance_name = :instanceName AND status NOT IN ('DELETED', 'TERMINATING') LIMIT 1")
  fun findByUserIdAndInstanceName(userId: String, instanceName: String): WorkbenchInstanceEntity?

  @Query("SELECT * FROM workbench_instances WHERE user_id = :userId AND status NOT IN ('DELETED', 'TERMINATING')")
  fun findByUserId(userId: String): List<WorkbenchInstanceEntity>

  @Query("SELECT * FROM workbench_instances WHERE user_id = :userId AND namespace = :namespace AND status NOT IN ('DELETED', 'TERMINATING')")
  fun findByUserIdAndNamespace(userId: String, namespace: String): List<WorkbenchInstanceEntity>
}
