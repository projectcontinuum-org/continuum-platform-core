## Purpose

Manages per-user `continuum-workbench` Kubernetes workloads — creates, suspends, resumes, updates, and deletes Deployment + Service + PVC triplets. Intended for cloud/multi-tenant deployments.

## Ownership

Core platform team. Container image: `projectcontinuum/continuum-cluster-manager`.

## Local Contracts

- K8s resources are templated via FreeMarker (`pvc.ftl`, `deployment.ftl`, `service.ftl` in `src/main/resources/templates/`). Template model keys: `instanceId`, `namespace`, `image`, `imagePullPolicy`, `cpuRequest/cpuLimit`, `memoryRequest/memoryLimit`, `storageSize`, `storageClassName`.
- Resource naming convention: `wb-{instanceId}-pvc`, `wb-{instanceId}-deployment`, `wb-{instanceId}-svc`.
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
