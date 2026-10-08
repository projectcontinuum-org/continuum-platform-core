apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: fw-${workerId}-pvc
  namespace: ${namespace}
  labels:
    app: continuum-feature-worker
    worker-id: "${workerId}"
    managed-by: continuum-cluster-manager
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: ${storageSize}
<#if storageClassName?has_content>
  storageClassName: ${storageClassName}
</#if>
