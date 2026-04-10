apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: wb-${instanceId}-pvc
  namespace: ${namespace}
  labels:
    app: continuum-workbench
    instance-id: "${instanceId}"
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
