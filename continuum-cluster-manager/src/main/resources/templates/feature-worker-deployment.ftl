apiVersion: apps/v1
kind: Deployment
metadata:
  name: fw-${workerId}-deployment
  namespace: ${namespace}
  labels:
    app: continuum-feature-worker
    worker-id: "${workerId}"
    managed-by: continuum-cluster-manager
spec:
  replicas: ${replicas?c}
  selector:
    matchLabels:
      app: continuum-feature-worker
      worker-id: "${workerId}"
  template:
    metadata:
      labels:
        app: continuum-feature-worker
        worker-id: "${workerId}"
        managed-by: continuum-cluster-manager
    spec:
      containers:
        - name: feature-worker
          image: ${image}
          imagePullPolicy: ${imagePullPolicy}
          env:
            - name: CONTINUUM_NODE_TASK_QUEUE
              value: "${taskQueue}"
<#list envVars?keys as k>
            - name: ${k}
              value: "${envVars[k]}"
</#list>
          resources:
            requests:
              cpu: "${cpuRequest}"
              memory: "${memoryRequest}"
            limits:
              cpu: "${cpuLimit}"
              memory: "${memoryLimit}"
<#if pvcEnabled>
          volumeMounts:
            - name: worker-storage
              mountPath: /workspace
</#if>
<#if pvcEnabled>
      volumes:
        - name: worker-storage
          persistentVolumeClaim:
            claimName: fw-${workerId}-pvc
</#if>
