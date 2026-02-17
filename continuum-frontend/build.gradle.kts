import com.github.gradle.node.yarn.task.YarnTask

plugins {
    id("com.github.node-gradle.node") version "3.2.1"
}

node {
    version.set("22.12.0") // Specify the Node.js version
    yarnVersion.set("1.22.22") // Specify the Yarn version
    download.set(true) // Automatically download and install Node.js
}

tasks.register<YarnTask>("yarnInstall") {
    args.set(listOf("install"))
}

tasks.register<YarnTask>("build") {
    dependsOn("yarnInstall")
    args.set(listOf("run", "build"))
}

tasks.register<Delete>("clean") {
    delete("continuum-workbench/lib")
    delete("workflow-editor-extension/lib")
}

tasks.named("build") {
    dependsOn("clean")
}

tasks.register<YarnTask>("run") {
    args.set(listOf("run", "start:workbench"))
}

tasks.register("publish") {
  description = "Publish the built application to JFrog Artifactory"
  group = "Publishing tasks"
  // don't publish yet
}

tasks.register<Exec>("jib") {
  description = "Docker build and push to GitHub Container Registry"
  group = "Jib tasks"
  val dockerRepoName = "ghcr.io/${(System.getenv("GITHUB_REPOSITORY") ?: "roushan65/continuum").lowercase()}"
  val imageName = "$dockerRepoName/${project.name.lowercase()}:${project.version}"
  commandLine("bash", "-c",
    "docker build -t $imageName . --progress=plain && " +
        "docker login ghcr.io --username ${System.getenv("DOCKER_REPO_USERNAME")} --password ${System.getenv("DOCKER_REPO_PASSWORD")} && " +
        "docker push $imageName"
  )
}