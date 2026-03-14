package org.projectcontinuum.core.orchestration.activity

import io.temporal.spring.boot.ActivityImpl
import org.projectcontinuum.core.commons.activity.IInitializeActivity
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
@ActivityImpl(taskQueues = [TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE])
class InitializeActivity(
    @param:Qualifier("orchestrationRestTemplate")
    private val restTemplate: RestTemplate,
    @param:Value("\${continuum.core.orchestration.api-server-base-url}")
    private val apiServerBaseUrl: String,
): IInitializeActivity {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(InitializeActivity::class.java)
        private const val TASK_QUEUES_ENDPOINT = "/api/v1/node-explorer/nodes/task-queues"
    }

    override fun getNodeTaskQueue(
        nodeIds: Set<String>
    ): Map<String, String> {
        if (nodeIds.isEmpty()) return emptyMap()

        val url = "${apiServerBaseUrl}${TASK_QUEUES_ENDPOINT}"
        LOGGER.info("Fetching task queues for {} node(s) from {}", nodeIds.size, url)

        try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val requestEntity = HttpEntity(nodeIds, headers)

            val responseType = object : ParameterizedTypeReference<Map<String, String>>() {}
            val response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, responseType)

            val taskQueueMap = response.body ?: emptyMap()
            LOGGER.info("Received task queues for {} node(s)", taskQueueMap.size)
            return taskQueueMap
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch task queues from {}: {}", url, e.message, e)
            throw RuntimeException("Failed to fetch task queues from API server", e)
        }
    }
}
