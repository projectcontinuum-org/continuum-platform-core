// Applied by every module that publishes an OpenAPI spec (api-server, cluster-manager, credentials-server).
//
// `generateOpenApiSpec` boots the service's test context and writes docs/api/<module>.openapi.yaml from the code, the same
// as `-PupdateOpenApiSpec`. `check` (and so `build`) depends on it, so a build never leaves the committed spec stale: the
// spec is regenerated first and OpenApiSpecTest then compares against it. The change shows up as a git diff in docs/api/,
// which CI (`openapi-spec-check.yml`) fails on until it is reviewed and committed.
//
// Usage in a module's build.gradle.kts, after its `tasks.withType<Test>` block: apply(from = rootProject.file("gradle/openapi-spec.gradle.kts"))

val committedOpenApiSpec = rootProject.file("docs/api/${project.name}.openapi.yaml")
val unitTests = tasks.named<Test>("test")

val generateOpenApiSpec = tasks.register<Test>("generateOpenApiSpec") {
    group = "documentation"
    description = "Regenerates docs/api/${project.name}.openapi.yaml from the code (runs OpenApiSpecTest in update mode)."

    useJUnitPlatform()
    testClassesDirs = unitTests.get().testClassesDirs
    classpath = unitTests.get().classpath
    filter { includeTestsMatching("*OpenApiSpecTest*") }

    systemProperty("continuum.openapi.spec-file", committedOpenApiSpec.absolutePath)
    systemProperty("continuum.openapi.update-spec", "true")

    // Always runs: the point is to rewrite the file from the current code, whatever Gradle's caches say.
    outputs.upToDateWhen { false }
}

// The regular test run compares against the file this task just wrote.
unitTests.configure { mustRunAfter(generateOpenApiSpec) }
tasks.named("check") { dependsOn(generateOpenApiSpec) }
