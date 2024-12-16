plugins {
  id("java-library")
  id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "com.continuum.core"
version = "1.0.0"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  api("org.apache.avro:avro:1.12.0")
}
