package org.projectcontinuum.core.cloud.gateway.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.projectcontinuum.core.cloud.gateway.service.WorkbenchProxyService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

// Reverse proxy controller that routes requests from
// /workbench/{instanceName}/open to the actual workbench service endpoint.
//
// This controller:
// - Resolves the workbench instance's K8s service endpoint from the database
// - Validates the requesting user owns the workbench instance
// - Proxies HTTP requests (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)
// - Strips the /workbench/{instanceName}/open prefix from the path
//
// WebSocket connections are handled separately by WorkbenchWebSocketProxyHandler.
@RestController
@RequestMapping("/workbench/{instanceName}/open")
class WorkbenchProxyController(
  private val workbenchProxyService: WorkbenchProxyService
) {

  private val logger = LoggerFactory.getLogger(WorkbenchProxyController::class.java)

  // Catch-all handler for all HTTP methods on /workbench/{instanceName}/open
  @RequestMapping(value = ["", "/**"])
  fun proxyRequest(
    @PathVariable instanceName: String,
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    request: HttpServletRequest,
    response: HttpServletResponse
  ) {
    workbenchProxyService.proxyRequest(instanceName, userId, request, response)
  }
}
