package org.projectcontinuum.core.api.server.entity.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.net.URI
import java.net.URISyntaxException

/**
 * `workflow_runs.workflow_uri` <-> [URI]. New rows always hold a valid URI: the workflow name is checked before a run
 * or schedule is accepted (`WorkflowRunValidator`). Rows written before that check (e.g. runs of a schedule whose name
 * contains a space) may not parse. Such a value is read with its illegal characters percent-encoded instead of
 * failing, because one unreadable row would otherwise make every run listing of its owner a 500.
 */
@Converter(autoApply = true)
class UriAttributeConverter : AttributeConverter<URI, String> {

    override fun convertToDatabaseColumn(attribute: URI?): String? {
        return attribute?.toString()
    }

    override fun convertToEntityAttribute(dbData: String?): URI? {
        return dbData?.let { storedValue -> parseLeniently(storedValue) }
    }

    private fun parseLeniently(storedValue: String): URI =
        try {
            URI(storedValue)
        } catch (e: URISyntaxException) {
            // The multi-argument constructor quotes every character that is illegal in a path.
            URI(null, null, storedValue, null)
        }
}
