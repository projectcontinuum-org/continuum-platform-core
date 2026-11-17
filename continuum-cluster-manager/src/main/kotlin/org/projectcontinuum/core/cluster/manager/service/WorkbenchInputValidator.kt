package org.projectcontinuum.core.cluster.manager.service

import io.fabric8.kubernetes.api.model.Quantity
import org.projectcontinuum.core.cluster.manager.config.WorkbenchProperties

/**
 * Validates every value that is interpolated into the FreeMarker K8s manifest templates
 * (`pvc.ftl`, `deployment.ftl`, `service.ftl`, `ingress.ftl`) and overlay templates.
 *
 * The templates interpolate raw strings into YAML, so a value containing a newline (or `---`)
 * could inject additional fields or whole extra resources. Every check here is a strict
 * allow-list regex over ASCII, so nothing that passes can contain whitespace, quotes or line
 * breaks. Violations throw [IllegalArgumentException], which `GlobalExceptionHandler` maps to 400.
 */
class WorkbenchInputValidator(private val workbenchProperties: WorkbenchProperties) {

  companion object {
    val ALLOWED_IMAGE_PULL_POLICIES = setOf("Always", "IfNotPresent", "Never")

    private const val MAX_QUANTITY_LENGTH = 32
    private const val MAX_IMAGE_LENGTH = 512
    private const val MAX_USER_ID_LENGTH = 256

    /** Plain decimal number with an optional K8s binary/decimal SI suffix or short exponent (no sign, no whitespace). */
    private val QUANTITY_REGEX =
      Regex("^(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:Ki|Mi|Gi|Ti|Pi|Ei|[numkMGTPE]|[eE][+-]?[0-9]{1,2})?$")

    /** RFC 1123 label (namespaces). */
    private val DNS1123_LABEL_REGEX = Regex("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$")

    /** RFC 1123 subdomain (storage class names), max 253 chars. */
    private val DNS1123_SUBDOMAIN_REGEX =
      Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$")

    // Image reference grammar (github.com/distribution/reference), ASCII only.
    private const val DOMAIN_COMPONENT = "(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])"
    private const val DOMAIN = "$DOMAIN_COMPONENT(?:\\.$DOMAIN_COMPONENT)*(?::[0-9]+)?"
    private const val PATH_COMPONENT = "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*"
    private const val NAME = "(?:$DOMAIN/)?$PATH_COMPONENT(?:/$PATH_COMPONENT)*"
    private const val TAG = "[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"
    private const val DIGEST = "[A-Za-z][A-Za-z0-9]*(?:[-_+.][A-Za-z][A-Za-z0-9]*)*:[0-9a-fA-F]{32,}"
    private val IMAGE_REFERENCE_REGEX = Regex("^$NAME(?::$TAG)?(?:@$DIGEST)?$")

    private const val DOCKER_HUB = "docker.io"
  }

  private val normalizedAllowedPrefixes: List<String> =
    workbenchProperties.allowedImagePrefixes
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { normalizePrefix(it) }

  /** CPU / memory / storage quantity, e.g. `500m`, `2`, `1Gi`. */
  fun validateQuantity(field: String, value: String) {
    require(value.length <= MAX_QUANTITY_LENGTH && QUANTITY_REGEX.matches(value)) {
      "Invalid $field '${sanitizeForMessage(value)}': must be a Kubernetes resource quantity such as 500m, 2 or 1Gi"
    }
    try {
      Quantity.getAmountInBytes(Quantity.parse(value))
    } catch (ex: RuntimeException) {
      throw IllegalArgumentException("Invalid $field '${sanitizeForMessage(value)}': not a parseable Kubernetes quantity")
    }
  }

  fun validateImagePullPolicy(value: String) {
    require(value in ALLOWED_IMAGE_PULL_POLICIES) {
      "Invalid imagePullPolicy '${sanitizeForMessage(value)}': must be one of $ALLOWED_IMAGE_PULL_POLICIES"
    }
  }

  /** `null` / blank means "use the cluster default" and is accepted. */
  fun validateStorageClassName(value: String?) {
    if (value.isNullOrEmpty()) return
    require(value.length <= 253 && DNS1123_SUBDOMAIN_REGEX.matches(value)) {
      "Invalid storageClassName '${sanitizeForMessage(value)}': must be a valid DNS-1123 subdomain"
    }
  }

  fun validateNamespace(value: String) {
    require(DNS1123_LABEL_REGEX.matches(value)) {
      "Invalid namespace '${sanitizeForMessage(value)}': must be a valid DNS-1123 label"
    }
  }

  /** Syntax only — use [validateRequestedImage] for images supplied by a caller. */
  fun validateImageSyntax(value: String) {
    require(value.length <= MAX_IMAGE_LENGTH && IMAGE_REFERENCE_REGEX.matches(value)) {
      "Invalid image '${sanitizeForMessage(value)}': must be a valid container image reference"
    }
  }

  /** Syntax check plus the `allowed-image-prefixes` allow-list (empty list = allow any valid reference). */
  fun validateRequestedImage(value: String) {
    validateImageSyntax(value)
    if (normalizedAllowedPrefixes.isEmpty()) return
    val normalized = normalizeImage(value)
    val allowed = normalizedAllowedPrefixes.any { prefix ->
      normalized == prefix ||
        (prefix.endsWith("/") && normalized.startsWith(prefix)) ||
        (normalized.startsWith(prefix) && normalized.length > prefix.length && normalized[prefix.length] in "/:@")
    }
    require(allowed) {
      "Image '$value' is not allowed: must start with one of ${workbenchProperties.allowedImagePrefixes}"
    }
  }

  /**
   * `userId` comes from the `x-continuum-user-id` header and is exposed to overlay templates.
   * Reject anything that could break out of a YAML scalar (line breaks, quotes, backslashes).
   */
  fun validateUserId(value: String) {
    require(
      value.isNotEmpty() && value.length <= MAX_USER_ID_LENGTH &&
        value.none { Character.isISOControl(it) || it == '"' || it == '\\' || it == '\u2028' || it == '\u2029' }
    ) {
      "Invalid user id: must be 1-$MAX_USER_ID_LENGTH characters without control characters, quotes or backslashes"
    }
  }

  fun validateResources(
    cpuRequest: String,
    cpuLimit: String,
    memoryRequest: String,
    memoryLimit: String,
    storageSize: String,
    storageClassName: String?
  ) {
    validateQuantity("cpuRequest", cpuRequest)
    validateQuantity("cpuLimit", cpuLimit)
    validateQuantity("memoryRequest", memoryRequest)
    validateQuantity("memoryLimit", memoryLimit)
    validateQuantity("storageSize", storageSize)
    validateStorageClassName(storageClassName)
  }

  /** Expands Docker Hub short names so `foo/bar` and `docker.io/foo/bar` compare equal. */
  private fun normalizeImage(image: String): String {
    val slash = image.indexOf('/')
    if (slash < 0) return "$DOCKER_HUB/library/$image"
    val first = image.substring(0, slash)
    if (!isRegistryHost(first)) return "$DOCKER_HUB/$image"
    val host = first.lowercase().let { if (it == "index.docker.io") DOCKER_HUB else it }
    val rest = image.substring(slash + 1)
    return if (host == DOCKER_HUB && !rest.contains('/')) "$DOCKER_HUB/library/$rest" else "$host/$rest"
  }

  /**
   * Normalises an allow-list entry the same way as [normalizeImage]. A trailing `/` (or a bare
   * registry host such as `ghcr.io`) means "anything below this path"; it is kept as a trailing `/`.
   */
  private fun normalizePrefix(prefix: String): String {
    val core = prefix.trimEnd('/')
    return when {
      !core.contains('/') && isRegistryHost(core) -> normalizeImage("$core/ns/x").removeSuffix("ns/x")
      prefix.endsWith("/") -> normalizeImage("$core/x").removeSuffix("x")
      else -> normalizeImage(core)
    }
  }

  private fun isRegistryHost(component: String): Boolean =
    component.contains('.') || component.contains(':') || component == "localhost"

  /** Error messages are returned to the caller; strip control characters and truncate. */
  private fun sanitizeForMessage(value: String): String =
    value.take(64).map { if (Character.isISOControl(it)) '?' else it }.joinToString("")
}
