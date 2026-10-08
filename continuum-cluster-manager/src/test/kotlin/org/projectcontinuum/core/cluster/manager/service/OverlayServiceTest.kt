package org.projectcontinuum.core.cluster.manager.service

import freemarker.template.Configuration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.projectcontinuum.core.cluster.manager.config.OverlayProperties
import java.nio.file.Files
import java.nio.file.Path

class OverlayServiceTest {

  @TempDir
  lateinit var overlayDir: Path

  private lateinit var service: OverlayService
  private lateinit var freemarkerCfg: Configuration

  private val baseDeploymentYaml = """
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: wb-test-123-deployment
      namespace: default
      labels:
        app: continuum-workbench
        instance-id: "test-123"
        managed-by: continuum-cluster-manager
    spec:
      replicas: 1
      selector:
        matchLabels:
          app: continuum-workbench
          instance-id: "test-123"
      template:
        metadata:
          labels:
            app: continuum-workbench
            instance-id: "test-123"
            managed-by: continuum-cluster-manager
        spec:
          securityContext:
            runAsUser: 1000
            runAsGroup: 1000
            fsGroup: 1000
          containers:
            - name: theia
              image: projectcontinuum/continuum-workbench:latest
              ports:
                - containerPort: 8080
              resources:
                requests:
                  cpu: "500m"
                  memory: "512Mi"
                limits:
                  cpu: "2"
                  memory: "1Gi"
              volumeMounts:
                - name: workspace-storage
                  mountPath: /workspace
          volumes:
            - name: workspace-storage
              persistentVolumeClaim:
                claimName: wb-test-123-pvc
  """.trimIndent()

  private val baseServiceYaml = """
    apiVersion: v1
    kind: Service
    metadata:
      name: wb-test-123-svc
      namespace: default
      labels:
        app: continuum-workbench
        instance-id: "test-123"
        managed-by: continuum-cluster-manager
    spec:
      type: ClusterIP
      selector:
        app: continuum-workbench
        instance-id: "test-123"
      ports:
        - protocol: TCP
          port: 8080
          targetPort: 8080
  """.trimIndent()

  private val basePvcYaml = """
    apiVersion: v1
    kind: PersistentVolumeClaim
    metadata:
      name: wb-test-123-pvc
      namespace: default
      labels:
        app: continuum-workbench
        instance-id: "test-123"
        managed-by: continuum-cluster-manager
    spec:
      accessModes:
        - ReadWriteOnce
      resources:
        requests:
          storage: 5Gi
  """.trimIndent()

  private val baseIngressYaml = """
    apiVersion: networking.k8s.io/v1
    kind: Ingress
    metadata:
      name: wb-test-123-ingress
      namespace: default
      labels:
        app: continuum-workbench
        instance-id: "test-123"
        managed-by: continuum-cluster-manager
    spec: {}
  """.trimIndent()

  private val defaultModel = mapOf<String, Any?>(
    "instanceId" to "test-123",
    "namespace" to "default",
    "userId" to "user-1",
    "image" to "projectcontinuum/continuum-workbench:latest",
    "imagePullPolicy" to "IfNotPresent",
    "cpuRequest" to "500m",
    "cpuLimit" to "2",
    "memoryRequest" to "512Mi",
    "memoryLimit" to "1Gi",
    "storageSize" to "5Gi",
    "storageClassName" to ""
  )

  @BeforeEach
  fun setUp() {
    freemarkerCfg = Configuration(Configuration.VERSION_2_3_34)
    freemarkerCfg.setClassLoaderForTemplateLoading(this::class.java.classLoader, "/templates")
    freemarkerCfg.defaultEncoding = "UTF-8"

    val properties = OverlayProperties(enabled = true, path = overlayDir.toString())
    service = OverlayService(properties, freemarkerCfg)
  }

  // ── disabled / no-op scenarios ─────────────────────────────────────────

  @Test
  fun `returns base unchanged when overlays disabled`() {
    val disabledService = OverlayService(OverlayProperties(enabled = false), freemarkerCfg)
    val result = disabledService.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    assertEquals(baseDeploymentYaml, result)
  }

  @Test
  fun `returns base unchanged when no variant specified`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), """
      metadata:
        annotations:
          custom: value
    """.trimIndent())

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, null)
    assertEquals(baseDeploymentYaml, result)
  }

  @Test
  fun `returns base unchanged when variant overlay file does not exist`() {
    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "nonexistent")
    assertEquals(baseDeploymentYaml, result)
  }

  @Test
  fun `returns base unchanged when overlay file is empty`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "")
    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    assertEquals(baseDeploymentYaml, result)
  }

  @Test
  fun `returns base unchanged when overlay file is whitespace only`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "   \n  \n  ")
    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    assertEquals(baseDeploymentYaml, result)
  }

  @Test
  fun `returns base unchanged when overlay directory does not exist`() {
    val missingDir = OverlayService(OverlayProperties(enabled = true, path = "/nonexistent/path"), freemarkerCfg)
    val result = missingDir.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    assertEquals(baseDeploymentYaml, result)
  }

  // ── variant deployment overlays ────────────────────────────────────────

  @Test
  fun `deployment overlay adds tolerations`() {
    val overlay = """
      spec:
        template:
          spec:
            tolerations:
              - key: "workbench"
                operator: "Equal"
                value: "true"
                effect: "NoSchedule"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("tolerations"))
    assertTrue(result.contains("workbench"))
    assertTrue(result.contains("NoSchedule"))
  }

  @Test
  fun `deployment overlay adds nodeSelector`() {
    val overlay = """
      spec:
        template:
          spec:
            nodeSelector:
              workload-type: workbench
              accelerator: nvidia-tesla-v100
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("nodeSelector"))
    assertTrue(result.contains("workload-type"))
    assertTrue(result.contains("nvidia-tesla-v100"))
  }

  @Test
  fun `deployment overlay adds annotations to metadata`() {
    val overlay = """
      metadata:
        annotations:
          custom.io/team: platform
          custom.io/env: production
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("annotations"))
    assertTrue(result.contains("custom.io/team"))
    assertTrue(result.contains("platform"))
  }

  @Test
  fun `deployment overlay adds initContainers`() {
    val overlay = """
      spec:
        template:
          spec:
            initContainers:
              - name: setup
                image: busybox:1.36
                command: ["sh", "-c", "echo setup complete"]
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("initContainers"))
    assertTrue(result.contains("setup"))
    assertTrue(result.contains("busybox"))
  }

  @Test
  fun `deployment overlay adds extra labels to metadata`() {
    val overlay = """
      metadata:
        labels:
          team: data-engineering
          environment: staging
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("team"))
    assertTrue(result.contains("data-engineering"))
    // Protected labels must still be present
    assertTrue(result.contains("continuum-workbench"))
    assertTrue(result.contains("test-123"))
    assertTrue(result.contains("continuum-cluster-manager"))
  }

  @Test
  fun `deployment overlay preserves existing securityContext when merging pod spec`() {
    val overlay = """
      spec:
        template:
          spec:
            tolerations:
              - key: "gpu"
                operator: "Exists"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    // The securityContext should still be present (it was in base, not overridden by overlay)
    assertTrue(result.contains("securityContext"))
    assertTrue(result.contains("runAsUser"))
    // And the overlay is also applied
    assertTrue(result.contains("tolerations"))
  }

  // ── variant service overlays ───────────────────────────────────────────

  @Test
  fun `service overlay adds annotations`() {
    val overlay = """
      metadata:
        annotations:
          service.beta.kubernetes.io/aws-load-balancer-internal: "true"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--service.yaml"), overlay)

    val result = service.applyOverlay(baseServiceYaml, ResourceType.SERVICE, defaultModel, "gpu")

    assertTrue(result.contains("annotations"))
    assertTrue(result.contains("aws-load-balancer-internal"))
  }

  // ── variant PVC overlays ───────────────────────────────────────────────

  @Test
  fun `pvc overlay adds storageClassName`() {
    val overlay = """
      spec:
        storageClassName: fast-ssd
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--pvc.yaml"), overlay)

    val result = service.applyOverlay(basePvcYaml, ResourceType.PVC, defaultModel, "gpu")

    assertTrue(result.contains("storageClassName"))
    assertTrue(result.contains("fast-ssd"))
  }

  @Test
  fun `pvc overlay adds annotations`() {
    val overlay = """
      metadata:
        annotations:
          volume.beta.kubernetes.io/storage-provisioner: ebs.csi.aws.com
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--pvc.yaml"), overlay)

    val result = service.applyOverlay(basePvcYaml, ResourceType.PVC, defaultModel, "gpu")

    assertTrue(result.contains("storage-provisioner"))
    assertTrue(result.contains("ebs.csi.aws.com"))
  }

  // ── variant ingress overlays ──────────────────────────────────────────

  @Test
  fun `ingress overlay adds annotations`() {
    val overlay = """
      metadata:
        annotations:
          nginx.ingress.kubernetes.io/rewrite-target: /
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), overlay)

    val result = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(result.contains("annotations"))
    assertTrue(result.contains("rewrite-target"))
  }

  @Test
  fun `ingress overlay adds rules and TLS`() {
    val overlay = """
      spec:
        rules:
          - host: workbench.example.com
            http:
              paths:
                - path: /
                  pathType: Prefix
                  backend:
                    service:
                      name: wb-test-123-svc
                      port:
                        number: 8080
        tls:
          - hosts:
              - workbench.example.com
            secretName: workbench-tls
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), overlay)

    val result = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(result.contains("rules"))
    assertTrue(result.contains("workbench.example.com"))
    assertTrue(result.contains("tls"))
    assertTrue(result.contains("workbench-tls"))
  }

  // ── FreeMarker template rendering in overlays ─────────────────────────

  @Test
  fun `overlay with FreeMarker variables renders userId`() {
    // Use FreeMarker syntax — note: dollar-brace is FreeMarker template syntax
    val overlay = "metadata:\n  annotations:\n    user-owner: \"\${userId}\""
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("user-owner"))
    assertTrue(result.contains("user-1"))
    assertFalse(result.contains("\${userId}"))
  }

  @Test
  fun `overlay with FreeMarker variables renders instanceId`() {
    val overlay = "spec:\n  rules:\n    - host: \"wb-\${instanceId}.example.com\""
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), overlay)

    val result = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(result.contains("wb-test-123.example.com"))
  }

  @Test
  fun `overlay with FreeMarker rendering error returns base unchanged`() {
    // Invalid FreeMarker — referencing undefined variable with strict mode
    val overlay = "metadata:\n  annotations:\n    bad: \"\${undefinedVariable}\""
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    // Should fall back to base YAML
    assertTrue(result.contains("wb-test-123-deployment"))
  }

  // ── protected field enforcement ────────────────────────────────────────

  @Test
  fun `overlay cannot change metadata name`() {
    val overlay = """
      metadata:
        name: hacked-name
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("wb-test-123-deployment"))
    assertFalse(result.contains("hacked-name"))
  }

  @Test
  fun `overlay cannot change metadata namespace`() {
    val overlay = """
      metadata:
        namespace: hacked-namespace
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("namespace: \"default\"") || result.contains("namespace: default"))
    assertFalse(result.contains("hacked-namespace"))
  }

  @Test
  fun `overlay cannot change instance-id label`() {
    val overlay = """
      metadata:
        labels:
          instance-id: "hacked-id"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("test-123"))
    assertFalse(result.contains("hacked-id"))
  }

  @Test
  fun `overlay cannot change app label`() {
    val overlay = """
      metadata:
        labels:
          app: hacked-app
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("continuum-workbench"))
    assertFalse(result.contains("hacked-app"))
  }

  @Test
  fun `overlay cannot change managed-by label`() {
    val overlay = """
      metadata:
        labels:
          managed-by: hacked-manager
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("continuum-cluster-manager"))
    assertFalse(result.contains("hacked-manager"))
  }

  @Test
  fun `overlay cannot change deployment selector matchLabels`() {
    val overlay = """
      spec:
        selector:
          matchLabels:
            instance-id: "hacked-selector"
            app: "hacked-selector-app"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), overlay)

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertFalse(result.contains("hacked-selector"))
  }

  @Test
  fun `overlay cannot change service selector labels`() {
    val overlay = """
      spec:
        selector:
          instance-id: "hacked-selector"
          app: "hacked-selector-app"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--service.yaml"), overlay)

    val result = service.applyOverlay(baseServiceYaml, ResourceType.SERVICE, defaultModel, "gpu")

    assertFalse(result.contains("hacked-selector"))
    assertFalse(result.contains("hacked-selector-app"))
  }

  @Test
  fun `ingress overlay cannot change metadata name`() {
    val overlay = """
      metadata:
        name: hacked-ingress
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), overlay)

    val result = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(result.contains("wb-test-123-ingress"))
    assertFalse(result.contains("hacked-ingress"))
  }

  @Test
  fun `ingress overlay cannot change protected labels`() {
    val overlay = """
      metadata:
        labels:
          instance-id: "hacked-id"
          app: "hacked-app"
          managed-by: "hacked-manager"
    """.trimIndent()
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), overlay)

    val result = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(result.contains("test-123"))
    assertTrue(result.contains("continuum-workbench"))
    assertTrue(result.contains("continuum-cluster-manager"))
    assertFalse(result.contains("hacked-id"))
    assertFalse(result.contains("hacked-app"))
    assertFalse(result.contains("hacked-manager"))
  }

  // ── error handling ─────────────────────────────────────────────────────

  @Test
  fun `malformed overlay YAML returns base unchanged`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "this: is: not: valid: yaml: [[[")

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    // Should fall back to base YAML
    assertTrue(result.contains("wb-test-123-deployment"))
  }

  @Test
  fun `overlay with non-object root returns base unchanged`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "just a string")

    val result = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")

    assertTrue(result.contains("wb-test-123-deployment"))
  }

  // ── resource type routing ──────────────────────────────────────────────

  @Test
  fun `each resource type reads its own variant overlay file`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), """
      metadata:
        annotations:
          overlay: deployment
    """.trimIndent())
    Files.writeString(overlayDir.resolve("gpu--service.yaml"), """
      metadata:
        annotations:
          overlay: service
    """.trimIndent())
    Files.writeString(overlayDir.resolve("gpu--pvc.yaml"), """
      metadata:
        annotations:
          overlay: pvc
    """.trimIndent())
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), """
      metadata:
        annotations:
          overlay: ingress
    """.trimIndent())

    val deploymentResult = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    val serviceResult = service.applyOverlay(baseServiceYaml, ResourceType.SERVICE, defaultModel, "gpu")
    val pvcResult = service.applyOverlay(basePvcYaml, ResourceType.PVC, defaultModel, "gpu")
    val ingressResult = service.applyOverlay(baseIngressYaml, ResourceType.INGRESS, defaultModel, "gpu")

    assertTrue(deploymentResult.contains("overlay: deployment") || deploymentResult.contains("overlay: \"deployment\""))
    assertTrue(serviceResult.contains("overlay: service") || serviceResult.contains("overlay: \"service\""))
    assertTrue(pvcResult.contains("overlay: pvc") || pvcResult.contains("overlay: \"pvc\""))
    assertTrue(ingressResult.contains("overlay: ingress") || ingressResult.contains("overlay: \"ingress\""))
  }

  @Test
  fun `different variants have isolated overlay files`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), """
      metadata:
        annotations:
          variant: gpu
    """.trimIndent())
    Files.writeString(overlayDir.resolve("cpu--deployment.yaml"), """
      metadata:
        annotations:
          variant: cpu
    """.trimIndent())

    val gpuResult = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    val cpuResult = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "cpu")

    assertTrue(gpuResult.contains("variant: gpu") || gpuResult.contains("variant: \"gpu\""))
    assertFalse(gpuResult.contains("variant: cpu") || gpuResult.contains("variant: \"cpu\""))
    assertTrue(cpuResult.contains("variant: cpu") || cpuResult.contains("variant: \"cpu\""))
    assertFalse(cpuResult.contains("variant: gpu") || cpuResult.contains("variant: \"gpu\""))
  }

  @Test
  fun `missing overlay for one type does not affect another`() {
    // Only deployment overlay exists for the gpu variant
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), """
      metadata:
        annotations:
          custom: value
    """.trimIndent())

    val deploymentResult = service.applyOverlay(baseDeploymentYaml, ResourceType.DEPLOYMENT, defaultModel, "gpu")
    val serviceResult = service.applyOverlay(baseServiceYaml, ResourceType.SERVICE, defaultModel, "gpu")

    assertTrue(deploymentResult.contains("custom"))
    assertEquals(baseServiceYaml, serviceResult)
  }

  // ── listVariants ──────────────────────────────────────────────────────

  @Test
  fun `listVariants returns discovered variant names`() {
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "spec: {}")
    Files.writeString(overlayDir.resolve("gpu--ingress.yaml"), "spec: {}")
    Files.writeString(overlayDir.resolve("cpu--deployment.yaml"), "spec: {}")
    Files.writeString(overlayDir.resolve("nvlink--deployment.yaml"), "spec: {}")

    val variants = service.listVariants()

    assertEquals(listOf("cpu", "gpu", "nvlink"), variants)
  }

  @Test
  fun `listVariants returns empty list when disabled`() {
    val disabledService = OverlayService(OverlayProperties(enabled = false), freemarkerCfg)
    val variants = disabledService.listVariants()
    assertTrue(variants.isEmpty())
  }

  @Test
  fun `listVariants returns empty list when no overlay files present`() {
    val variants = service.listVariants()
    assertTrue(variants.isEmpty())
  }

  @Test
  fun `listVariants ignores files without variant naming convention`() {
    Files.writeString(overlayDir.resolve("deployment.yaml"), "spec: {}")
    Files.writeString(overlayDir.resolve("gpu--deployment.yaml"), "spec: {}")

    val variants = service.listVariants()

    assertEquals(listOf("gpu"), variants)
  }
}
