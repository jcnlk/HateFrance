plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes collected artifacts and every version's build output."
    delete(layout.buildDirectory)
    dependsOn(stonecutter.tasks.named("clean"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds every Minecraft version and collects its JAR in build/libs."
    dependsOn(stonecutter.tasks.named("build"))
    dependsOn(stonecutter.tasks.named("collectJar"))
}

tasks.register("buildActive") {
    group = "build"
    description = "Builds only the active Minecraft version."
    dependsOn(stonecutter.tasks.named("build") { metadata.isActive })
}

tasks.register("runActive") {
    group = "fabric"
    description = "Runs the client for the active Minecraft version."
    dependsOn(stonecutter.tasks.named("runClient") { metadata.isActive })
}
