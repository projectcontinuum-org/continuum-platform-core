package org.projectcontinuum.core.knime.scheduler.exception

import java.util.UUID

class KnimeWorkflowNotFoundException(workflowId: UUID) :
  RuntimeException("KNIME workflow not found: $workflowId")
