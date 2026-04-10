package org.projectcontinuum.core.cluster.manager.config

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KubernetesClientConfig(
  @param:Value("\${continuum.core.cluster-manager.kubernetes.master-url:}")
  private val masterUrl: String,
  @param:Value("\${continuum.core.cluster-manager.kubernetes.kubeconfig:}")
  private val kubeconfig: String
) {

  @Bean
  fun kubernetesClient(): KubernetesClient {
    val builder = KubernetesClientBuilder()
    val configBuilder = io.fabric8.kubernetes.client.ConfigBuilder()

    if (masterUrl.isNotBlank()) {
      configBuilder.withMasterUrl(masterUrl)
    }
    if (kubeconfig.isNotBlank()) {
      configBuilder.withAutoOAuthToken(null)
      System.setProperty("kubeconfig", kubeconfig)
    }

    builder.withConfig(configBuilder.build())
    return builder.build()
  }
}
