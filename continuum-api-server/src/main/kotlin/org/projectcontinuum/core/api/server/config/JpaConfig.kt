package org.projectcontinuum.core.api.server.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["org.projectcontinuum.core.api.server.repository.jpa"])
class JpaConfig
