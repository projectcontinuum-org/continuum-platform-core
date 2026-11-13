## Purpose

Manages per-user `continuum-workbench` Kubernetes workloads — creates, suspends, resumes, updates, and deletes Deployment + Service + PVC triplets. Intended for cloud/multi-tenant deployments.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-cluster-manager`.

## Local Contracts

- K8s resources are templated via FreeMarker (`pvc.ftl`, `deployment.ftl`, `service.ftl`, `ingress.ftl` in `src/main/resources/templates/`). Template model keys: `instanceId`, `namespace`, `userId`, `image`, `imagePullPolicy`, `cpuRequest/cpuLimit`, `memoryRequest/memoryLimit`, `storageSize`, `storageClassName`.
- Resource naming convention: `wb-{instanceId}-pvc`, `wb-{instanceId}-deployment`, `wb-{instanceId}-svc`, `wb-{instanceId}-ingress` (`OverlayService.ResourceType.expectedName`).
- Manifest-injection guard, layer 1 (input validation): every template value must pass `WorkbenchInputValidator` before it is rendered. A violation throws `IllegalArgumentException`, which becomes a 400. The rules:
  - cpu/memory/storage quantities: strict regex, max 32 chars, then Fabric8 `Quantity.parse`.
  - `imagePullPolicy`: one of `Always`/`IfNotPresent`/`Never`.
  - `storageClassName`: null/empty or a DNS-1123 subdomain.
  - `namespace`: a DNS-1123 label.
  - `userId`: no control chars, quotes or backslashes.
  - image: a valid reference (distribution grammar).
  - Requested images must also start with an entry of `continuum.core.cluster-manager.workbench.allowed-image-prefixes` (`WORKBENCH_ALLOWED_IMAGE_PREFIXES`). The default is `image-repository`; an empty list allows any valid reference. Docker Hub short names are normalised, and a prefix matches at a `/`, `:` or `@` boundary.
  - Create/update requests are validated in `WorkbenchService`. Every render (`buildTemplateModel`) re-validates the stored entity, so resume/rollback of a tampered row fails too. Stored entities are not re-checked against the allow-list.
  - `imagePullPolicy` and `namespace` are config-only and are validated at startup.
- Layer 2 (rendered-manifest verification): `applyYaml` verifies the final YAML, after overlays, before any `createOrReplace`. It must contain exactly one resource of the expected apiVersion/kind, named `wb-{instanceId}-{suffix}`, in the configured namespace, carrying the three standard labels. Anything else throws `IllegalStateException` (500). Overlays may add or patch fields but must not change kind, name, namespace or those labels, or add documents. Templates must quote string scalars with `?json_string`.
- Overlay variants (`variant` on create, `continuum.core.cluster-manager.workbench.overlays.*`): the name must match `^[A-Za-z0-9][A-Za-z0-9._-]{0,62}$` with no `--`. When overlays are enabled it must also be a directory listed by `listVariants()`. Unknown or invalid variants give 400 (`OverlayService.requireKnownVariant`). `applyOverlay` additionally requires the resolved file's parent to be the variant directory (canonical-path check) and throws rather than falling back to the base manifest.
- The workbench PVC is mounted at `/home/continuum` (the image's uid-1000 user), so Theia settings (`~/.theia`), Claude Code state (`~/.claude`, `~/.claude.json`) and the workflows (`~/workspace`, reached through the image's `/workspace` symlink) persist together. Theia opens `/home/continuum/workspace` by default (positional backend argument in the image's `CMD`), so workbench URLs need no `#/...` path. The image therefore keeps nothing it needs inside that directory and its `entrypoint.sh` seeds it on every start.
- All workloads carry labels `instance-id`, `app=continuum-workbench`, `managed-by=continuum-cluster-manager` — used for bulk delete by label.
- Suspend = delete Deployment + Service, keep PVC. Resume = recreate Deployment + Service from stored entity.
- K8s mutation always happens before DB write. If K8s fails, the DB is not updated (DB is the final record of confirmed state). Rollback is attempted on failure.
- `refreshStatusFromK8s()` queries the live deployment on `GET /status` calls — DB status may lag K8s.
- Service endpoint exposed as `wb-{instanceId}-svc.{namespace}.svc.cluster.local:8080` — used by `continuum-cloud-gateway` to proxy requests.
- All operations emit structured `AUDIT |` log lines.
- Config: `continuum.core.cluster-manager.kubernetes.master-url`, `continuum.core.cluster-manager.workbench.*`.
- Requires PostgreSQL for workbench instance registry.

## Work Guidance

- In local dev without Kubernetes, `KUBERNETES_MASTER_URL` and `KUBECONFIG` can be left blank; the Fabric8 client will attempt to auto-detect from `~/.kube/config`.
- The `WorkbenchInstanceRepository` is a plain Spring Data JPA repository; tests can use H2.

## Verification

```bash
./gradlew :continuum-cluster-manager:test
```
