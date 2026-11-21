package org.projectcontinuum.core.cluster.manager.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.projectcontinuum.core.cluster.manager.config.OpenApiConfig
import org.projectcontinuum.core.cluster.manager.model.WorkbenchCreateRequest
import org.projectcontinuum.core.cluster.manager.model.WorkbenchLivenessResponse
import org.projectcontinuum.core.cluster.manager.model.WorkbenchResponse
import org.projectcontinuum.core.cluster.manager.model.WorkbenchUpdateRequest
import org.projectcontinuum.core.cluster.manager.service.DockerHubService
import org.projectcontinuum.core.cluster.manager.service.DockerHubTag
import org.projectcontinuum.core.cluster.manager.service.OverlayService
import org.projectcontinuum.core.cluster.manager.service.WorkbenchService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("/api/v1/workbench", produces = ["application/json"])
class WorkbenchController(
  private val workbenchService: WorkbenchService,
  private val dockerHubService: DockerHubService,
  private val overlayService: OverlayService
) {

  companion object {
    private const val INSTANCE_NAME_DESCRIPTION = "Name of one of the caller's workbenches."
    private const val NOT_FOUND_EXAMPLE =
      """{"type":"about:blank","title":"Not Found","status":404,"detail":"Workbench 'analysis' not found for user 'alice'","instance":"/api/v1/workbench/analysis","error":"Workbench 'analysis' not found for user 'alice'"}"""
    private const val WORKBENCH_EXAMPLE =
      """{"instanceId":"4c2e8a61-0b9f-4f0e-9a52-6d3f1e7b2c90","instanceName":"analysis","namespace":"continuum-workbench","userId":"alice","status":"RUNNING","image":"projectcontinuum/continuum-workbench:0.0.14","resources":{"cpuRequest":"500m","cpuLimit":"2","memoryRequest":"512Mi","memoryLimit":"2Gi","storageSize":"10Gi","storageClassName":null},"overlayVariant":"GPU","serviceEndpoint":"wb-4c2e8a61-0b9f-4f0e-9a52-6d3f1e7b2c90-svc.continuum-workbench.svc.cluster.local:8080","ingressUrl":"https://wb-4c2e8a61.continuum.example.com","createdAt":"2026-11-21T09:00:00Z","updatedAt":"2026-11-21T09:05:00Z"}"""
  }

  @GetMapping("/tags")
  @Tag(name = OpenApiConfig.TAG_WORKBENCH_CATALOG)
  @Operation(
    operationId = "listWorkbenchImageTags",
    summary = "List the available workbench image tags",
    description = """
Returns up to 100 tags of the configured workbench image repository (`WORKBENCH_IMAGE_REPOSITORY`) from Docker Hub,
most recently updated first, for the image picker of the management UI. Cached for 5 minutes; when Docker Hub fails
the last cached list is returned. Not user-scoped. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The tags.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = DockerHubTag::class)))]
  )
  @ApiResponse(
    responseCode = "502",
    description = "Docker Hub could not be reached and nothing is cached.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Bad Gateway","status":502,"detail":"Failed to fetch data from external service","instance":"/api/v1/workbench/tags","error":"Failed to fetch data from external service"}""")]
      )
    ]
  )
  fun getAvailableTags(): ResponseEntity<List<DockerHubTag>> {
    val tags = dockerHubService.getAvailableTags()
    return ResponseEntity.ok(tags)
  }

  @GetMapping("/variants")
  @Tag(name = OpenApiConfig.TAG_WORKBENCH_CATALOG)
  @Operation(
    operationId = "listWorkbenchVariants",
    summary = "List the overlay variants a workbench can be created with",
    description = """
Returns the names of the configured overlay variants (sets of manifest overlays, e.g. a GPU node pool), sorted. Empty
when overlays are disabled. Not user-scoped. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "Variant names.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(type = "string", example = "GPU")))]
  )
  fun getAvailableVariants(): ResponseEntity<List<String>> {
    val variants = overlayService.listVariants()
    return ResponseEntity.ok(variants)
  }

  // Any Accept: Traefik ForwardAuth forwards the browser's own headers (a page load sends Accept: text/html), and the
  // answer has no body. The class-level produces = application/json would turn every such check into a 406.
  @GetMapping("/authorize", produces = [MediaType.ALL_VALUE])
  @Tag(name = OpenApiConfig.TAG_ACCESS_CONTROL)
  @Operation(
    operationId = "authorizeWorkbenchAccess",
    summary = "Decide whether a user may open a workbench (Traefik ForwardAuth)",
    description = """
Internal: only in the `internal` spec group. Called by the Traefik `workbench-forward-auth` middleware for every request
to a workbench. Allows (200) when `x-continuum-user-id` owns the RUNNING workbench whose Ingress host is
`X-Forwarded-Host`, and denies (403) otherwise. Empty bodies. Lookups are cached."""
  )
  @ApiResponse(responseCode = "200", description = "Access allowed.")
  @ApiResponse(responseCode = "403", description = "Access denied: no running workbench of this user has this host.")
  @ApiResponse(
    responseCode = "400",
    description = "`x-continuum-user-id` or `X-Forwarded-Host` is missing.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA))]
  )
  fun authorize(
    @Parameter(name = "x-continuum-user-id", `in` = ParameterIn.HEADER, required = true, description = "The authenticated user, as forwarded by Traefik.", example = "alice")
    @RequestHeader("x-continuum-user-id") userId: String,
    @Parameter(name = "X-Forwarded-Host", `in` = ParameterIn.HEADER, required = true, description = "Host of the original workbench request, set by Traefik.", example = "wb-4c2e8a61.continuum.example.com")
    @RequestHeader("X-Forwarded-Host") forwardedHost: String
  ): ResponseEntity<Void> {
    val authorized = workbenchService.authorizeAccess(userId, forwardedHost)
    return if (authorized) ResponseEntity.ok().build() else ResponseEntity.status(HttpStatus.FORBIDDEN).build()
  }

  @PostMapping(consumes = ["application/json"])
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "createWorkbench",
    summary = "Create a workbench for the caller",
    description = """
Creates the caller's workbench: a PersistentVolumeClaim, a Deployment, a Service and an Ingress named
`wb-<instanceId>-*` in the workbench namespace, rendered from the templates and the optional overlay variant. Every
value is validated before anything is created; if a Kubernetes step fails, the resources created so far are removed
again. The record is stored only after all resources exist. Returns when Kubernetes accepted the resources; poll
`liveness` until the pod is ready. A partial `resources` object is fine: omitted fields take their defaults. Not
idempotent: a second create with the same name is a 409."""
  )
  @OpenApiRequestBody(
    description = "The workbench to create.",
    required = true,
    content = [
      Content(
        mediaType = "application/json",
        examples = [
          ExampleObject(
            name = "gpu",
            summary = "GPU variant with more memory",
            value = """{"instanceName":"analysis","image":"projectcontinuum/continuum-workbench:0.0.14","variant":"GPU","resources":{"cpuRequest":"500m","cpuLimit":"2","memoryRequest":"512Mi","memoryLimit":"2Gi","storageSize":"10Gi","storageClassName":null}}"""
          ),
          ExampleObject(name = "defaults", summary = "Default image and resources", value = """{"instanceName":"analysis"}""")
        ]
      )
    ]
  )
  @ApiResponse(
    responseCode = "201",
    description = "Workbench created.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchResponse::class), examples = [ExampleObject(value = WORKBENCH_EXAMPLE)])]
  )
  @ApiResponse(
    responseCode = "400",
    description = "A value is invalid (user id, quantity, image or variant), the image is not allowed, `instanceName` is missing, or the body is not JSON.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Bad Request","status":400,"detail":"Invalid memoryLimit '1Gi?---?kind: Secret': must be a Kubernetes quantity of at most 32 characters","instance":"/api/v1/workbench","error":"Invalid memoryLimit '1Gi?---?kind: Secret': must be a Kubernetes quantity of at most 32 characters"}""")]
      )
    ]
  )
  @ApiResponse(
    responseCode = "409",
    description = "The caller already has an active (running or suspended) workbench with this name.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Conflict","status":409,"detail":"Workbench 'analysis' already exists for user 'alice'","instance":"/api/v1/workbench","error":"Workbench 'analysis' already exists for user 'alice'"}""")]
      )
    ]
  )
  fun createWorkbench(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @RequestBody request: WorkbenchCreateRequest
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.createWorkbench(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping("/{instanceName}")
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "getWorkbench",
    summary = "Get one of the caller's workbenches with a fresh status",
    description = """
Returns the workbench after refreshing its status from its Deployment (RUNNING when a replica is ready, PENDING when
not, UNKNOWN when the Deployment is missing) and storing the change. A SUSPENDED workbench has no Deployment on
purpose and keeps its status."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The workbench.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchResponse::class), examples = [ExampleObject(value = WORKBENCH_EXAMPLE)])]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun getWorkbenchStatus(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.getWorkbenchStatus(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{instanceName}/liveness")
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "getWorkbenchLiveness",
    summary = "Check whether a workbench is ready to open",
    description = """
True only when the workbench is RUNNING and its pod reports a ready replica. The management UI polls this after a
create or resume. Unlike `GET /{instanceName}` it never changes the stored status. Read-only."""
  )
  @ApiResponse(responseCode = "200", description = "Readiness.", content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchLivenessResponse::class), examples = [ExampleObject(value = """{"ready":true,"status":"RUNNING"}""")])])
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun getWorkbenchLiveness(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchLivenessResponse> {
    val response = workbenchService.getWorkbenchLiveness(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @DeleteMapping("/{instanceName}")
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "deleteWorkbench",
    summary = "Delete a workbench and its volume",
    description = """
Deletes the workbench's Deployment, Service, Ingress **and PersistentVolumeClaim** (the user's files are lost), then
marks it DELETED. If Kubernetes fails, nothing is marked. A second delete is a 404."""
  )
  @ApiResponse(responseCode = "204", description = "Deleted.")
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun deleteWorkbench(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String
  ): ResponseEntity<Void> {
    workbenchService.deleteWorkbench(userId, instanceName)
    return ResponseEntity.noContent().build()
  }

  @GetMapping
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "listWorkbenches",
    summary = "List the caller's workbenches",
    description = "Returns the caller's workbenches that are not deleted, with their stored status (not refreshed from Kubernetes). Read-only."
  )
  @ApiResponse(
    responseCode = "200",
    description = "The workbenches.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = WorkbenchResponse::class)))]
  )
  fun listWorkbenches(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String
  ): ResponseEntity<List<WorkbenchResponse>> {
    val response = workbenchService.listWorkbenches(userId)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}/suspend")
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "suspendWorkbench",
    summary = "Suspend a workbench, keeping its volume",
    description = """
Deletes the workbench's Deployment, Service and Ingress but keeps its PersistentVolumeClaim, so it stops using compute
while the user's files stay. Resume it to get it back. Suspending a suspended workbench is a 400."""
  )
  @ApiResponse(responseCode = "200", description = "Suspended.", content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchResponse::class))])
  @ApiResponse(
    responseCode = "400",
    description = "The workbench is already suspended.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Bad Request","status":400,"detail":"Workbench 'analysis' is already suspended","instance":"/api/v1/workbench/analysis/suspend","error":"Workbench 'analysis' is already suspended"}""")]
      )
    ]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun suspendWorkbench(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.suspendWorkbench(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}/resume")
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "resumeWorkbench",
    summary = "Resume a suspended workbench",
    description = """
Recreates the Deployment, Service and Ingress of a suspended workbench from its stored settings, on its existing
volume. If a step fails, the partly created resources are removed again. Resuming a workbench that is not suspended
is a 400."""
  )
  @ApiResponse(responseCode = "200", description = "Resumed (RUNNING).", content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchResponse::class))])
  @ApiResponse(
    responseCode = "400",
    description = "The workbench is not suspended.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Bad Request","status":400,"detail":"Workbench 'analysis' is not suspended","instance":"/api/v1/workbench/analysis/resume","error":"Workbench 'analysis' is not suspended"}""")]
      )
    ]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun resumeWorkbench(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.resumeWorkbench(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}", consumes = ["application/json"])
  @Tag(name = OpenApiConfig.TAG_WORKBENCHES)
  @SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
  @Operation(
    operationId = "updateWorkbench",
    summary = "Change a workbench's image or resources",
    description = """
Re-applies the workbench's Deployment, Service and Ingress with the given image and/or resources; omitted fields keep
their value. If a step fails, the previous manifests are applied again. Kubernetes restarts the pod for the change.
A SUSPENDED workbench only stores the change and stays suspended; it is applied on resume."""
  )
  @OpenApiRequestBody(
    description = "The changes.",
    required = true,
    content = [
      Content(
        mediaType = "application/json",
        examples = [ExampleObject(name = "newImage", summary = "Upgrade the image", value = """{"image":"projectcontinuum/continuum-workbench:0.0.15"}""")]
      )
    ]
  )
  @ApiResponse(responseCode = "200", description = "Updated.", content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkbenchResponse::class))])
  @ApiResponse(
    responseCode = "400",
    description = "The image is invalid or not allowed, or a quantity is invalid.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA))]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no active workbench with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = NOT_FOUND_EXAMPLE)])]
  )
  fun updateWorkbench(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = INSTANCE_NAME_DESCRIPTION, example = "analysis")
    @PathVariable instanceName: String,
    @RequestBody request: WorkbenchUpdateRequest
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.updateWorkbench(userId, instanceName, request)
    return ResponseEntity.ok(response)
  }
}
