package org.projectcontinuum.core.cloud.gateway.service

import org.projectcontinuum.core.cloud.gateway.repository.WorkbenchInstanceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PongMessage
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.AbstractWebSocketHandler
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

// WebSocket proxy handler that forwards WebSocket connections from
// /workbench/{instanceName}/open to the target workbench instance.
//
// For each incoming WebSocket connection, this handler:
// 1. Extracts the instanceName from the URI path
// 2. Validates the user owns the workbench instance
// 3. Resolves the target service endpoint
// 4. Establishes a WebSocket connection to the target
// 5. Bidirectionally forwards all messages between client and target
@Component
class WorkbenchWebSocketProxyHandler(
  private val repository: WorkbenchInstanceRepository
) : AbstractWebSocketHandler() {

  private val logger = LoggerFactory.getLogger(WorkbenchWebSocketProxyHandler::class.java)
  private val webSocketClient = StandardWebSocketClient()

  // Maps inbound session ID -> outbound (target) session
  private val sessionMap = ConcurrentHashMap<String, WebSocketSession>()

  override fun afterConnectionEstablished(session: WebSocketSession) {
    val uri = session.uri ?: run {
      session.close(CloseStatus.SERVER_ERROR.withReason("Missing URI"))
      return
    }

    // Extract instanceName from the path: /workbench/{instanceName}/open/...
    val pathParts = uri.path.split("/")
    // Expected: ["", "workbench", "{instanceName}", "open", ...]
    if (pathParts.size < 4 || pathParts[2].isBlank()) {
      session.close(CloseStatus.BAD_DATA.withReason("Invalid path: cannot extract instanceName"))
      return
    }
    val instanceName = pathParts[2]

    // Extract userId from handshake headers
    val userId = session.handshakeHeaders.getFirst("x-continuum-user-id") ?: "anonymous"

    // Resolve workbench instance
    val entity = repository.findByUserIdAndInstanceName(userId, instanceName)
    if (entity == null) {
      logger.warn("WebSocket proxy: workbench '{}' not found for user '{}'", instanceName, userId)
      session.close(CloseStatus.POLICY_VIOLATION.withReason("Workbench not found"))
      return
    }

    if (entity.status != "RUNNING") {
      logger.warn("WebSocket proxy: workbench '{}' is not running (status: {})", instanceName, entity.status)
      session.close(CloseStatus.SERVICE_RESTARTED.withReason("Workbench is not running"))
      return
    }

    // Build target WebSocket URL
    val serviceEndpoint = "wb-${entity.instanceId}-svc.${entity.namespace}.svc.cluster.local:8080"
    val prefixPath = "/workbench/$instanceName/open"
    val remainingPath = uri.path.removePrefix(prefixPath).let {
      if (it.isEmpty() || !it.startsWith("/")) "/$it" else it
    }
    val query = uri.rawQuery?.let { "?$it" } ?: ""
    val targetWsUrl = "ws://$serviceEndpoint$remainingPath$query"

    logger.debug("WebSocket proxy: {} -> {}", uri, targetWsUrl)

    try {
      // Create a handler for the outbound (target) WebSocket session
      val targetHandler = TargetWebSocketHandler(session)

      // Connect to target
      val targetSession = webSocketClient
        .execute(targetHandler, null, URI.create(targetWsUrl))
        .get() // blocking connect

      sessionMap[session.id] = targetSession
      logger.info("WebSocket proxy established: client={} -> target={}", session.id, targetSession.id)
    } catch (e: Exception) {
      logger.error("WebSocket proxy: failed to connect to target '{}': {}", targetWsUrl, e.message, e)
      session.close(CloseStatus.SERVER_ERROR.withReason("Failed to connect to workbench"))
    }
  }

  override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
    val targetSession = sessionMap[session.id]
    if (targetSession != null && targetSession.isOpen) {
      targetSession.sendMessage(message)
    }
  }

  override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
    val targetSession = sessionMap[session.id]
    if (targetSession != null && targetSession.isOpen) {
      targetSession.sendMessage(message)
    }
  }

  override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
    val targetSession = sessionMap[session.id]
    if (targetSession != null && targetSession.isOpen) {
      targetSession.sendMessage(message)
    }
  }

  override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
    logger.error("WebSocket proxy transport error for session {}: {}", session.id, exception.message)
    val targetSession = sessionMap.remove(session.id)
    if (targetSession != null && targetSession.isOpen) {
      try {
        targetSession.close(CloseStatus.SERVER_ERROR)
      } catch (_: Exception) { /* ignore */ }
    }
  }

  override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
    logger.debug("WebSocket proxy: client session {} closed with status {}", session.id, status)
    val targetSession = sessionMap.remove(session.id)
    if (targetSession != null && targetSession.isOpen) {
      try {
        targetSession.close(status)
      } catch (_: Exception) { /* ignore */ }
    }
  }

  override fun supportsPartialMessages(): Boolean = true

  // Handler for the outbound WebSocket connection to the target workbench instance.
  // Forwards all messages received from the target back to the client.
  private inner class TargetWebSocketHandler(
    private val clientSession: WebSocketSession
  ) : AbstractWebSocketHandler() {

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
      if (clientSession.isOpen) {
        clientSession.sendMessage(message)
      }
    }

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
      if (clientSession.isOpen) {
        clientSession.sendMessage(message)
      }
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
      if (clientSession.isOpen) {
        clientSession.sendMessage(message)
      }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
      logger.error("WebSocket proxy: target transport error: {}", exception.message)
      if (clientSession.isOpen) {
        try {
          clientSession.close(CloseStatus.SERVER_ERROR)
        } catch (_: Exception) { /* ignore */ }
      }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
      logger.debug("WebSocket proxy: target session closed with status {}", status)
      sessionMap.remove(clientSession.id)
      if (clientSession.isOpen) {
        try {
          clientSession.close(status)
        } catch (_: Exception) { /* ignore */ }
      }
    }

    override fun supportsPartialMessages(): Boolean = true
  }
}
