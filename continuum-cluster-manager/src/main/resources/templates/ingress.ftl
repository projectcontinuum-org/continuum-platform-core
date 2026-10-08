apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: wb-${instanceId}-ingress
  namespace: ${namespace}
  labels:
    app: continuum-workbench
    instance-id: "${instanceId}"
    managed-by: continuum-cluster-manager
spec: {}
