import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node") version "3.2.1"
}

// Read version and name from package.json
val packageJsonFile = file("package.json")
val packageJson = groovy.json.JsonSlurper().parseText(packageJsonFile.readText()) as Map<*, *>
version = property("platformVersion").toString()

node {
    version.set("22.12.0") // Specify the Node.js version
    download.set(true) // Automatically download and install Node.js
}


tasks.register<NpmTask>("build") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

tasks.register<Delete>("clean") {
    delete("dist")
}

tasks.named("build") {
    dependsOn("clean")
}

tasks.register<NpmTask>("run") {
    args.set(listOf("run", "dev"))
}

tasks.register("publish") {
    description = "Publish the built application to JFrog Artifactory"
    group = "Publishing tasks"
    // don't publish yet
}

tasks.register<Exec>("jib") {
    description = "Docker build and push to Docker Hub"
    group = "Jib tasks"
    val dockerRepoOwner = (System.getenv("GITHUB_REPOSITORY_OWNER") ?: property("repoOwner").toString()).lowercase()
    val dockerRepoName = "docker.io/$dockerRepoOwner/${project.name.lowercase()}"
    val imageName = "$dockerRepoName:${project.version}"
    val latestImageName = "$dockerRepoName:latest"
    commandLine("bash", "-c",
        "docker build -t $imageName -t $latestImageName . --progress=plain && " +
            "docker login docker.io --username ${System.getenv("DOCKER_REPO_USERNAME")} --password ${System.getenv("DOCKER_REPO_PASSWORD")} && " +
            "docker push $imageName && " +
            "docker push $latestImageName"
    )
}

