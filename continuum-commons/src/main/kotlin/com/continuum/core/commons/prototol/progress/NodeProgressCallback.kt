package com.continuum.core.commons.prototol.progress

interface NodeProgressCallback {
  fun report(
    nodeProgress: NodeProgress
  )
  fun report(
    progressPercentage: Int
  )
}