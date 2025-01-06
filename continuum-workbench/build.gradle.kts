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
    delete("build")
}

tasks.named("build") {
    dependsOn("clean")
}
