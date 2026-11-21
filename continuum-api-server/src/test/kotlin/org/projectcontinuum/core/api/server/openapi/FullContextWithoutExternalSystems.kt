package org.projectcontinuum.core.api.server.openapi

import io.temporal.client.WorkflowClient
import io.temporal.client.schedules.ScheduleClient
import io.temporal.serviceclient.WorkflowServiceStubs
import org.duckdb.DuckDBConnection
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

/**
 * The external systems of the API server, replaced by mocks so the full application context starts without
 * Temporal, S3/MinIO or the bundled DuckDB extension. PostgreSQL is replaced by in-memory H2 through the
 * properties below; nothing in the OpenAPI tests reads a table.
 */
abstract class FullContextWithoutExternalSystems {

  @MockitoBean
  lateinit var workflowServiceStubs: WorkflowServiceStubs

  @MockitoBean
  lateinit var workflowClient: WorkflowClient

  @MockitoBean
  lateinit var scheduleClient: ScheduleClient

  @MockitoBean
  lateinit var s3AsyncClient: S3AsyncClient

  @MockitoBean
  lateinit var s3TransferManager: S3TransferManager

  @MockitoBean
  lateinit var duckDbConnection: DuckDBConnection

  companion object {
    /**
     * `src/test/resources/application.yml` shadows the main one on the test classpath, so the main file is imported
     * explicitly: the OpenAPI tests check the real springdoc and server settings, not test copies of them.
     */
    const val MAIN_APPLICATION_YML = "spring.config.import=file:src/main/resources/application.yml"

    /** Shared by every full-context test, so they reuse one cached Spring context per flag combination. */
    const val H2_DATASOURCE = "spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
    const val H2_DRIVER = "spring.datasource.driver-class-name=org.h2.Driver"
    const val H2_USER = "spring.datasource.username=sa"
    const val H2_PASSWORD = "spring.datasource.password="
    /**
     * PostgreSQL dialect on purpose: the RSQL auto-configuration (`RSQLJPAAutoConfiguration.entityManagerDatabase`)
     * references Hibernate's removed `DerbyDialect` for any other dialect. No SQL runs in these tests.
     */
    const val H2_DIALECT = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
    const val NO_SCHEMA_INIT = "spring.sql.init.mode=never"
    const val NO_MINIO_BUCKET_INIT = "continuum.core.api-server.storage.type=aws-s3"
    const val TEMPORAL_TARGET = "temporal.connection.target=localhost:7233"
  }
}
