package org.projectcontinuum.core.api.server.entity.jpa

import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UriAttributeConverterTest {

  private val converter = UriAttributeConverter()

  @Test
  fun `a valid stored URI is read unchanged`() {
    assertEquals(URI("examples/text-cleanup.cwf"), converter.convertToEntityAttribute("examples/text-cleanup.cwf"))
  }

  @Test
  fun `a stored value that is not a valid URI is read percent-encoded instead of failing`() {
    val readBack = converter.convertToEntityAttribute("Nightly KNIME run")

    assertEquals("Nightly%20KNIME%20run", readBack?.rawPath)
    assertEquals("Nightly KNIME run", readBack?.path)
  }

  @Test
  fun `null stays null both ways`() {
    assertNull(converter.convertToEntityAttribute(null))
    assertNull(converter.convertToDatabaseColumn(null))
  }
}
