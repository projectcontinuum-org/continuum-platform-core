package org.projectcontinuum.core.api.server.entity.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.net.URI

@Converter(autoApply = true)
class UriAttributeConverter : AttributeConverter<URI, String> {

    override fun convertToDatabaseColumn(attribute: URI?): String? {
        return attribute?.toString()
    }

    override fun convertToEntityAttribute(dbData: String?): URI? {
        return dbData?.let { URI.create(it) }
    }
}

