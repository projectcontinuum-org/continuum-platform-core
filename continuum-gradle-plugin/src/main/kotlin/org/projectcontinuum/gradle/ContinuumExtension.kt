package org.projectcontinuum.gradle

import org.gradle.api.provider.Property

abstract class ContinuumExtension {

    /** Version of continuum-commons and continuum-worker-springboot-starter */
    abstract val continuumVersion: Property<String>

    /** Maven group ID for continuum core artifacts (commons, worker-starter) */
    abstract val continuumGroup: Property<String>

    /** Whether to use Kotlin (true) or plain Java (false). Default: true */
    abstract val useKotlin: Property<Boolean>

    /** Spring Boot BOM version */
    abstract val springBootVersion: Property<String>

    /** Spring Cloud BOM version */
    abstract val springCloudVersion: Property<String>

    /** Temporal BOM version */
    abstract val temporalVersion: Property<String>

    /** AWS SDK BOM version */
    abstract val awsSdkVersion: Property<String>

    /** Jackson module version */
    abstract val jacksonVersion: Property<String>

    /** Whether to configure Maven Central publishing via JReleaser */
    abstract val publishToMavenCentral: Property<Boolean>
}
