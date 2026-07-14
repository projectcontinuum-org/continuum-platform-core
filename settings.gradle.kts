pluginManagement {
    plugins {
        kotlin("jvm") version "2.2.0"
        kotlin("plugin.spring") version "2.2.0"
        kotlin("plugin.jpa") version "2.2.0"
    }
}

rootProject.name = "continuum-platform-core"

include(":continuum-commons")
include(":continuum-message-bridge")
include(":continuum-avro-schemas")
include(":continuum-worker-springboot-starter")
include(":continuum-api-server")
include(":continuum-orchestration-service")
include(":continuum-gradle-plugin")
include(":continuum-cluster-manager")
include(":continuum-cloud-gateway")
include(":continuum-credentials-server")
include(":landing-page")
