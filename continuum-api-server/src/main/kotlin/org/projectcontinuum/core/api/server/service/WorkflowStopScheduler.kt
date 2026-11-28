package org.projectcontinuum.core.api.server.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Timer
import java.util.TimerTask

/**
 * Runs the hard stop of a cancel or terminate request after the grace period that lets the run handle its stop signal
 * first. The request itself returns at once; tests replace this with one that runs the stop immediately.
 */
fun interface WorkflowStopScheduler {
  fun schedule(delayMillis: Long, stop: () -> Unit)
}

/**
 * Runs every delayed stop on one daemon timer thread, so a pending stop never keeps the JVM alive. A stop that throws
 * is logged and the timer keeps running: an uncaught exception would end the timer thread and silently drop every
 * later stop.
 */
@Component
class TimerWorkflowStopScheduler : WorkflowStopScheduler {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(TimerWorkflowStopScheduler::class.java)
  }

  private val timer = Timer("workflow-stop-scheduler", true)

  override fun schedule(delayMillis: Long, stop: () -> Unit) {
    val task = object : TimerTask() {
      override fun run() {
        try {
          stop()
        } catch (e: Exception) {
          LOGGER.warn("A delayed workflow stop failed", e)
        }
      }
    }
    timer.schedule(task, delayMillis)
  }
}
