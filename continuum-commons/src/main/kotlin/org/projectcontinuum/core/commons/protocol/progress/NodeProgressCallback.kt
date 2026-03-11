package org.projectcontinuum.core.commons.protocol.progress

interface NodeProgressCallback {
  fun report(
    nodeProgress: NodeProgress
  )
  fun report(
    progressPercentage: Int
  )
}