apiVersion: v1
kind: Service
metadata:
  name: wb-${instanceId}-svc
  namespace: ${namespace}
  labels:
    app: continuum-workbench
    instance-id: "${instanceId}"
    managed-by: continuum-cluster-manager
spec:
  type: ClusterIP
  selector:
    app: continuum-workbench
    instance-id: "${instanceId}"
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
