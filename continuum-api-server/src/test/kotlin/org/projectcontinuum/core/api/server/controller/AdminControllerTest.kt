package org.projectcontinuum.core.api.server.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.exception.ApiExceptionHandler
import org.projectcontinuum.core.api.server.model.ScheduleValidationReport
import org.projectcontinuum.core.api.server.model.ScheduleValidationResult
import org.projectcontinuum.core.api.server.service.ScheduleValidationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals

class AdminControllerTest {

  private val endpoint = "/api/v1/admin/schedules/validate"

  private val report = ScheduleValidationReport(
    checked = 3,
    invalid = listOf(
      ScheduleValidationResult(
        scheduleId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = "Nightly",
        ownedBy = "alice",
        problems = listOf(ScheduleValidationResult.Problem("n1", "org.test.Typed", "INVALID_PROPERTIES", "/width: string found, integer expected"))
      )
    ),
    unregisteredOnly = emptyList(),
    failedToCheck = emptyList()
  )

  @Test
  fun `group lists are trimmed and blanks dropped`() {
    assertEquals(setOf("a", "b c", "d"), AdminController.parseGroups(" a ,, b c ,d, "))
    assertEquals(emptySet(), AdminController.parseGroups(null))
    assertEquals(emptySet(), AdminController.parseGroups(" , ,"))
  }

  @Nested
  @WebMvcTest(AdminController::class)
  @Import(ApiExceptionHandler::class)
  @TestPropertySource(properties = ["continuum.core.api-server.admin.groups=platform-admins, ops"])
  inner class WithAdminGroups {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var scheduleValidationService: ScheduleValidationService

    @Test
    fun `a caller in an admin group runs the check and gets the report`() {
      whenever(scheduleValidationService.validateAll()).thenReturn(report)

      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", "developers,platform-admins"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.checked").value(3))
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.invalid[0].scheduleId").value("00000000-0000-0000-0000-000000000001"))
        .andExpect(jsonPath("$.invalid[0].ownedBy").value("alice"))
        .andExpect(jsonPath("$.invalid[0].problems[0].nodeId").value("n1"))
        .andExpect(jsonPath("$.invalid[0].problems[0].kind").value("INVALID_PROPERTIES"))
        .andExpect(jsonPath("$.invalid[0].problems[0].detail").value("/width: string found, integer expected"))

      verify(scheduleValidationService).validateAll()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ops", " ops ", "x, ops", "ops,,", "platform-admins"])
    fun `any configured admin group is accepted, with spaces around entries ignored`(groups: String) {
      whenever(scheduleValidationService.validateAll()).thenReturn(report)

      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", groups)).andExpect(status().isOk)
    }

    @ParameterizedTest
    @ValueSource(strings = ["developers", "", " , ", "platform-admin", "Platform-Admins", "OPS", "platform-admins-x", "developers;ops"])
    fun `a caller in no admin group is a 404 and nothing is checked`(groups: String) {
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", groups)).andExpect(status().isNotFound)

      verifyNoInteractions(scheduleValidationService)
    }

    @Test
    fun `a missing groups header is a 404`() {
      mockMvc.perform(post(endpoint)).andExpect(status().isNotFound)

      verifyNoInteractions(scheduleValidationService)
    }

    @Test
    fun `the user id header is not a group`() {
      mockMvc.perform(post(endpoint).header("x-continuum-user-id", "platform-admins")).andExpect(status().isNotFound)

      verifyNoInteractions(scheduleValidationService)
    }

    @Test
    fun `a repeated groups header is matched across all its values`() {
      whenever(scheduleValidationService.validateAll()).thenReturn(report)

      // Spring joins repeated headers with commas before binding to a String.
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", "developers").header("x-continuum-user-groups", "ops"))
        .andExpect(status().isOk)
    }

    @Test
    fun `only POST triggers the check`() {
      mockMvc.perform(get(endpoint).header("x-continuum-user-groups", "ops")).andExpect(status().isMethodNotAllowed)

      verifyNoInteractions(scheduleValidationService)
    }
  }

  @Nested
  @WebMvcTest(AdminController::class)
  @Import(ApiExceptionHandler::class)
  inner class WithoutAdminGroups {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var scheduleValidationService: ScheduleValidationService

    @Test
    fun `with no admin groups configured the endpoint is a 404 for everyone`() {
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", "platform-admins")).andExpect(status().isNotFound)
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", "")).andExpect(status().isNotFound)
      mockMvc.perform(post(endpoint)).andExpect(status().isNotFound)

      verifyNoInteractions(scheduleValidationService)
    }
  }

  @Nested
  @WebMvcTest(AdminController::class)
  @Import(ApiExceptionHandler::class)
  @TestPropertySource(properties = ["continuum.core.api-server.admin.groups= , ,"])
  inner class WithBlankAdminGroups {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var scheduleValidationService: ScheduleValidationService

    @Test
    fun `a blank admin group list disables the endpoint, it does not match an empty group`() {
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", " , ")).andExpect(status().isNotFound)
      mockMvc.perform(post(endpoint).header("x-continuum-user-groups", "")).andExpect(status().isNotFound)

      verifyNoInteractions(scheduleValidationService)
    }
  }
}
