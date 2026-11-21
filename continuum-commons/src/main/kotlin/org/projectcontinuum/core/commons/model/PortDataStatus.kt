package org.projectcontinuum.core.commons.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "PortDataStatus", description = "`SUCCESS`: the port was produced. `WARNING`: produced with warnings. `FAILED`: the node failed (port `\$error`).")
enum class PortDataStatus {
  SUCCESS,
  FAILED,
  WARNING
}
