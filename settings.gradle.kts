rootProject.name = "continuum"

gradle.extra["temporalBomVersion"] = "1.28.0"

include(":continuum-commons")
include(":continuum-avro-schemas")
include(":continuum-worker")
include(":continuum-api-server")
include("continuum-frontend")