package org.projectcontinuum.core.cluster.manager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.cluster-manager.workbench")
data class WorkbenchProperties(
  val defaultImage: String = "projectcontinuum/continuum-workbench:latest",
  val namespace: String = "default",
  val imageRepository: String = "projectcontinuum/continuum-workbench",
  val imagePullPolicy: String = "IfNotPresent",
  /**
   * Image repository prefixes a create/update request may use (e.g. `projectcontinuum/continuum-workbench`,
   * `ghcr.io/acme/`). Docker Hub short names are normalised (`foo/bar` == `docker.io/foo/bar`).
   * An entry matches the image itself or anything below it at a `/`, `:` or `@` boundary.
   * Defaults to [imageRepository]. Empty list = any syntactically valid image reference is allowed.
   */
  val allowedImagePrefixes: List<String> = listOf(imageRepository)
)
