package org.projectcontinuum.core.cluster.manager.model

enum class FeatureWorkerStatus {
  PENDING,
  RUNNING,
  FAILED,
  UNKNOWN,
  TERMINATING,
  DELETED
}
