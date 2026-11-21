package org.projectcontinuum.core.cluster.manager.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(name = "Workbench", description = "One of the caller's workbenches.")
data class WorkbenchResponse(
  @field:Schema(description = "Server-generated id; the Kubernetes resources are named `wb-<instanceId>-*`.", example = "4c2e8a61-0b9f-4f0e-9a52-6d3f1e7b2c90")
  val instanceId: UUID,
  @field:Schema(description = "Name chosen by the user.", example = "analysis")
  val instanceName: String,
  @field:Schema(description = "Kubernetes namespace of the workbench resources.", example = "continuum-workbench")
  val namespace: String,
  @field:Schema(description = "Owner (the `x-continuum-user-id` that created it).", example = "alice")
  val userId: String,
  @field:Schema(
    description = "`PENDING`: created, pod not ready yet. `RUNNING`: pod ready. `SUSPENDED`: only the volume is kept. " +
      "`UNKNOWN`: the Deployment was not found when the status was refreshed. `FAILED`, `TERMINATING`, `DELETED`: " +
      "lifecycle end states (deleted workbenches are not listed).",
    allowableValues = ["PENDING", "RUNNING", "FAILED", "SUSPENDED", "UNKNOWN", "TERMINATING", "DELETED"],
    example = "RUNNING"
  )
  val status: String,
  @field:Schema(description = "Container image.", example = "projectcontinuum/continuum-workbench:0.0.14")
  val image: String,
  @field:Schema(description = "Compute and storage.")
  val resources: ResourceSpec,
  @field:Schema(description = "Overlay variant it was created with; null for the base manifests.", nullable = true, example = "GPU")
  val overlayVariant: String?,
  @field:Schema(description = "In-cluster address of the workbench Service.", nullable = true, example = "wb-4c2e8a61-0b9f-4f0e-9a52-6d3f1e7b2c90-svc.continuum-workbench.svc.cluster.local:8080")
  val serviceEndpoint: String?,
  @field:Schema(description = "External URL (`https://` + the rendered Ingress host); null when the Ingress has no host or the workbench is suspended.", nullable = true, example = "https://wb-4c2e8a61.continuum.example.com")
  val ingressUrl: String?,
  @field:Schema(description = "When it was created (ISO-8601, UTC).", example = "2026-11-21T09:00:00Z")
  val createdAt: Instant,
  @field:Schema(description = "When it last changed (ISO-8601, UTC).", example = "2026-11-21T09:05:00Z")
  val updatedAt: Instant
)
