package org.projectcontinuum.core.cluster.manager.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties

class WorkbenchInputValidatorTest {

  private val validator = WorkbenchInputValidator(WorkbenchProperties())
  private val permissive = WorkbenchInputValidator(WorkbenchProperties(allowedImagePrefixes = emptyList()))

  // ── quantities ──────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = ["500m", "2", "0.5", "1Gi", "512Mi", "50Gi", "1G", "1e3", "100k", ".5"])
  fun `valid quantities are accepted`(value: String) {
    assertDoesNotThrow { validator.validateQuantity("cpuRequest", value) }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "1Gi\n  x: y",
      "1Gi\n---\napiVersion: v1\nkind: Secret",
      "1Gi ",
      " 1Gi",
      "1 Gi",
      "1Gi\"",
      "-1",
      "+1",
      "abc",
      "",
      "1Gb",
      "1e3m",
      "1Gi#",
      "1Gi\r"
    ]
  )
  fun `hostile or malformed quantities are rejected`(value: String) {
    assertThrows<IllegalArgumentException> { validator.validateQuantity("memoryLimit", value) }
  }

  // ── imagePullPolicy ─────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = ["Always", "IfNotPresent", "Never"])
  fun `known pull policies are accepted`(value: String) {
    assertDoesNotThrow { validator.validateImagePullPolicy(value) }
  }

  @ParameterizedTest
  @ValueSource(strings = ["always", "Sometimes", "", "Always\n---\nkind: Secret"])
  fun `unknown pull policies are rejected`(value: String) {
    assertThrows<IllegalArgumentException> { validator.validateImagePullPolicy(value) }
  }

  // ── storageClassName ────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = ["fast-ssd", "gp3", "standard", "ebs.csi.aws.com"])
  fun `DNS-1123 storage class names are accepted`(value: String) {
    assertDoesNotThrow { validator.validateStorageClassName(value) }
  }

  @Test
  fun `null or empty storage class name is accepted`() {
    assertDoesNotThrow { validator.validateStorageClassName(null) }
    assertDoesNotThrow { validator.validateStorageClassName("") }
  }

  @ParameterizedTest
  @ValueSource(strings = ["../../etc", "Fast-SSD", "fast ssd", "fast-", "-fast", "fast\n  x: y", "a/b", "fast_ssd"])
  fun `invalid storage class names are rejected`(value: String) {
    assertThrows<IllegalArgumentException> { validator.validateStorageClassName(value) }
  }

  // ── image ───────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
    strings = [
      "projectcontinuum/continuum-workbench:0.0.5",
      "projectcontinuum/continuum-workbench",
      "docker.io/projectcontinuum/continuum-workbench:latest",
      "index.docker.io/projectcontinuum/continuum-workbench:latest",
      "projectcontinuum/continuum-workbench@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    ]
  )
  fun `images under the default allowed prefix are accepted`(value: String) {
    assertDoesNotThrow { validator.validateRequestedImage(value) }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "evil/continuum-workbench:latest",
      "projectcontinuum/continuum-workbench-evil:latest",
      "ghcr.io/projectcontinuum/continuum-workbench:latest",
      "busybox"
    ]
  )
  fun `images outside the allowed prefixes are rejected`(value: String) {
    assertThrows<IllegalArgumentException> { validator.validateRequestedImage(value) }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "my image:latest",
      "image:latest\n---\napiVersion: v1\nkind: Secret",
      "image:latest\n  command: [sh]",
      "image:latest\"",
      "image:\$(whoami)",
      "Image:Latest",
      "image:",
      ""
    ]
  )
  fun `syntactically invalid images are rejected even with an empty allow-list`(value: String) {
    assertThrows<IllegalArgumentException> { permissive.validateRequestedImage(value) }
  }

  @Test
  fun `empty allow-list allows any valid reference`() {
    assertDoesNotThrow { permissive.validateRequestedImage("ghcr.io/acme/tools:1.2.3") }
    assertDoesNotThrow { permissive.validateRequestedImage("localhost:5000/wb") }
  }

  @Test
  fun `registry and namespace prefixes match at path boundaries`() {
    val v = WorkbenchInputValidator(WorkbenchProperties(allowedImagePrefixes = listOf("ghcr.io/acme/", "registry.local:5000")))
    assertDoesNotThrow { v.validateRequestedImage("ghcr.io/acme/wb:1") }
    assertDoesNotThrow { v.validateRequestedImage("registry.local:5000/team/wb:1") }
    assertThrows<IllegalArgumentException> { v.validateRequestedImage("ghcr.io/acme-evil/wb:1") }
    assertThrows<IllegalArgumentException> { v.validateRequestedImage("registry.local:50001/wb:1") }
  }

  // ── namespace / userId ──────────────────────────────────────────────

  @Test
  fun `namespace must be a DNS-1123 label`() {
    assertDoesNotThrow { validator.validateNamespace("default") }
    assertThrows<IllegalArgumentException> { validator.validateNamespace("default\n---") }
    assertThrows<IllegalArgumentException> { validator.validateNamespace("Default") }
  }

  @Test
  fun `userId rejects line breaks and quotes`() {
    assertDoesNotThrow { validator.validateUserId("user-1@example.com") }
    assertThrows<IllegalArgumentException> { validator.validateUserId("u\n---\nkind: Secret") }
    assertThrows<IllegalArgumentException> { validator.validateUserId("u\"x") }
    assertThrows<IllegalArgumentException> { validator.validateUserId("") }
  }
}
