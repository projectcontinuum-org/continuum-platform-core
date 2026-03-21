package org.projectcontinuum.core.api.server.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["org.projectcontinuum.core.api.server.repository.jpa"])
@EntityScan(basePackages = ["org.projectcontinuum.core.api.server.entity.jpa"])
class JpaConfig {

    @Bean
    fun objectMapperSupplier(objectMapper: ObjectMapper): ObjectMapperSupplier {
        return ObjectMapperSupplier { objectMapper }
    }
}
