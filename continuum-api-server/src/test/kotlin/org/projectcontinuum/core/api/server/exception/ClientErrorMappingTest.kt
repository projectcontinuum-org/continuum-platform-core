package org.projectcontinuum.core.api.server.exception

import cz.jirutka.rsql.parser.ParseException
import cz.jirutka.rsql.parser.RSQLParserException
import io.github.perplexhub.rsql.ConversionException
import io.github.perplexhub.rsql.UnknownPropertyException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.controller.WorkflowRunController
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.projectcontinuum.core.api.server.service.WorkflowRunService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Client mistakes in RSQL filters are 400 problems, not 500s. */
@WebMvcTest(WorkflowRunController::class)
class ClientErrorMappingTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowRunService: WorkflowRunService

  private fun stubRunFilterFailure(failure: RuntimeException) {
    whenever(
      workflowRunService.findAll(ownedBy = eq("anonymous"), filter = anyOrNull(), page = any(), size = any(), sort = anyOrNull())
    ).thenThrow(failure)
  }

  @Test
  fun `unparsable RSQL is a 400 problem`() {
    stubRunFilterFailure(RSQLParserException(ParseException("Encountered \"<EOF>\" at line 1, column 5.")))

    mockMvc.perform(get("/api/v1/workflow-runs").param("filter", "((bad"))
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.title").value("Invalid filter"))
      .andExpect(jsonPath("$.detail").value("Invalid filter: it is not valid RSQL syntax"))
  }

  @Test
  fun `an unknown RSQL field is a 400 problem`() {
    stubRunFilterFailure(UnknownPropertyException("noSuchField", WorkflowRunSummaryEntity::class.java))

    mockMvc.perform(get("/api/v1/workflow-runs").param("filter", "noSuchField==1"))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.detail").value("Invalid filter: it names a field that cannot be filtered on"))
  }

  @Test
  fun `an RSQL value of the wrong type is a 400 problem`() {
    stubRunFilterFailure(ConversionException("Failed to convert notadate to java.time.Instant type", IllegalArgumentException()))

    mockMvc.perform(get("/api/v1/workflow-runs").param("filter", "createdAt==notadate"))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Invalid filter: a value has the wrong type")))
  }
}
