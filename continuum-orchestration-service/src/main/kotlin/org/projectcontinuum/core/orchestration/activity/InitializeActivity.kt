package org.projectcontinuum.core.orchestration.activity

import io.temporal.failure.ApplicationFailure
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
import org.springframework.web.client.RestClientException
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

        val taskQueueMap = try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val requestEntity = HttpEntity(nodeIds, headers)

            val responseType = object : ParameterizedTypeReference<Map<String, String>>() {}
            val response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, responseType)

            val result = response.body ?: emptyMap()
            LOGGER.info("Received task queues for {} node(s)", result.size)
            result
        } catch (e: RestClientException) {
            LOGGER.error("Failed to fetch task queues from {}: {}", url, e.message, e)
            throw ApplicationFailure.newFailureWithCause(
                "Failed to fetch task queues from API server. Will retry.",
                "ApiServerFailure",
                e
            )
        }

        // Check for missing task queues and throw an exception to trigger a retry if any are missing
        val missingNodes = nodeIds - taskQueueMap.keys
        if (missingNodes.isNotEmpty()) {
            LOGGER.warn("Missing task queues for {} node(s): {}", missingNodes.size, missingNodes)
            throw ApplicationFailure.newFailureWithCause(
                "Task queues not yet available for nodes: $missingNodes. Will retry.",
                "MissingTaskQueues",
              RuntimeException("Missing task queues for nodes: $missingNodes")
            )
        }

        return taskQueueMap
    }
}
