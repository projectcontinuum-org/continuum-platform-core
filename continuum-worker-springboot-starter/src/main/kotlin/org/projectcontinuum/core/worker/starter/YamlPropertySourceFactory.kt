package org.projectcontinuum.core.worker.starter

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.io.support.EncodedResource
import org.springframework.core.io.support.PropertySourceFactory

/**
 * A custom [PropertySourceFactory] that enables loading YAML files
 * via Spring's [@PropertySource][org.springframework.context.annotation.PropertySource] annotation.
 *
 * By default, `@PropertySource` only supports `.properties` files.
 * This factory uses [YamlPropertiesFactoryBean] to parse YAML resources
 * and expose them as a [PropertiesPropertySource].
 *
 * @author Continuum Team
 * @since 1.0.0
 */
class YamlPropertySourceFactory : PropertySourceFactory {

    override fun createPropertySource(name: String?, resource: EncodedResource): PropertySource<*> {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(resource.resource)
        val properties = factory.getObject()
            ?: throw IllegalStateException("Could not load YAML properties from ${resource.resource.description}")
        val sourceName = name ?: resource.resource.filename ?: "yamlPropertySource"
        return PropertiesPropertySource(sourceName, properties)
    }
}

