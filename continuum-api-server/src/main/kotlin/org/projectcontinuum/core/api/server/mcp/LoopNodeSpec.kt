package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.commons.model.LoopNode

/** How the compact MCP spec names a loop: a node whose nodeId is the built-in loop id; its members set `parent`. */
internal object LoopNodeSpec {

  fun isLoop(nodeId: String?): Boolean = nodeId == LoopNode.NODE_MODEL

  /** The React Flow type a built workflow gives the node: `Loop` for a loop, none (left to the builder) otherwise. */
  fun typeOf(nodeId: String?): String? = if (isLoop(nodeId = nodeId)) LoopNode.NODE_TYPE else null
}
