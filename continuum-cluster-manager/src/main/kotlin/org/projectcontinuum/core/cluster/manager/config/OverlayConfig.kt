package org.projectcontinuum.core.cluster.manager.config

import freemarker.template.Configuration
import org.projectcontinuum.core.cluster.manager.service.OverlayService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean

/**
 * Produces the per-domain [OverlayService] beans. Workbench and feature-worker
 * overlays are configured independently (separate enabled flags, directories,
 * and protected label sets) but share the same merge/snapshot/restore logic.
 */
@org.springframework.context.annotation.Configuration
class OverlayConfig {

  @Bean
  @Qualifier("workbenchOverlayService")
  fun workbenchOverlayService(
    overlayProperties: OverlayProperties,
    freemarkerConfig: Configuration
  ): OverlayService {
    val service = OverlayService(
      overlayProperties = overlayProperties,
      freemarkerConfig = freemarkerConfig,
      protectedLabels = listOf("instance-id", "app", "managed-by")
    )
    service.init()
    return service
  }

  @Bean
  @Qualifier("featureWorkerOverlayService")
  fun featureWorkerOverlayService(
    overlayProperties: FeatureWorkerOverlayProperties,
    freemarkerConfig: Configuration
  ): OverlayService {
    val service = OverlayService(
      overlayProperties = overlayProperties,
      freemarkerConfig = freemarkerConfig,
      protectedLabels = listOf("worker-id", "app", "managed-by")
    )
    service.init()
    return service
  }
}
