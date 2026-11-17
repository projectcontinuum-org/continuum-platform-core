apiVersion: apps/v1
kind: Deployment
metadata:
  name: wb-${instanceId}-deployment
  namespace: ${namespace}
  labels:
    app: continuum-workbench
    instance-id: "${instanceId}"
    managed-by: continuum-cluster-manager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: continuum-workbench
      instance-id: "${instanceId}"
  template:
    metadata:
      labels:
        app: continuum-workbench
        instance-id: "${instanceId}"
        managed-by: continuum-cluster-manager
    spec:
      securityContext:
        runAsUser: 1000
        runAsGroup: 1000
        fsGroup: 1000
      containers:
        - name: theia
          image: "${image?json_string}"
          imagePullPolicy: "${imagePullPolicy?json_string}"
          env:
            - name: NODE_ENV
              value: "production"
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "${cpuRequest?json_string}"
              memory: "${memoryRequest?json_string}"
            limits:
              cpu: "${cpuLimit?json_string}"
              memory: "${memoryLimit?json_string}"
          volumeMounts:
            - name: workspace-storage
              mountPath: /home/continuum
          startupProbe:
            httpGet:
              path: /
              port: 8080
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            tcpSocket:
              port: 8080
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /
              port: 8080
            periodSeconds: 10
            timeoutSeconds: 5

      volumes:
        - name: workspace-storage
          persistentVolumeClaim:
            claimName: wb-${instanceId}-pvc
