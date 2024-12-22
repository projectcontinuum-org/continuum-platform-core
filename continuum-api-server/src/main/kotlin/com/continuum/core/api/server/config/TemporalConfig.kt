package com.continuum.core.api.server.config

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TemporalConfig(
    @Value("\${temporal.connection.target}")
    private val temporalServiceAddress: String
) {

    @Bean
    fun workflowServiceStubs(): WorkflowServiceStubs {
        return WorkflowServiceStubs
            .newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(temporalServiceAddress).build())
    }

    @Bean
    fun workflowClient(
        @Value("\${temporal.connection.namespace}")
        temporalNamespace: String,
        workflowServiceStubs: WorkflowServiceStubs
    ): WorkflowClient {
        return WorkflowClient.newInstance(
            workflowServiceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(temporalNamespace)
                .build()
        )
    }

}