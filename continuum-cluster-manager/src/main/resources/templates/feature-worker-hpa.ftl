apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: fw-${workerId}-hpa
  namespace: ${namespace}
  labels:
    app: continuum-feature-worker
    worker-id: "${workerId}"
    managed-by: continuum-cluster-manager
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: fw-${workerId}-deployment
  minReplicas: ${autoscalingMinReplicas?c}
  maxReplicas: ${autoscalingMaxReplicas?c}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: ${autoscalingTargetCpuPercent?c}
