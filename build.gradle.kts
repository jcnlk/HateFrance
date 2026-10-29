import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

version = "${project.property("mod_version")}+${sc.current.version}"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

repositories {
    maven { url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") }
    maven("https://repo.essential.gg/repository/maven-public") {
        name = "Essential"
        content {
            includeGroup("gg.essential")
        }
    }
    ivy("https://github.com/Noamm9/NoammAddons/releases/download") {
        name = "NoammAddonsReleases"
        patternLayout {
            artifact("[revision]/[artifact]-[revision](-[classifier]).[ext]")
        }
        metadataSources {
            artifact()
        }
        content {
            includeModule("com.github.Noamm9", "NoammAddons")
        }
    }
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
        content {
            includeGroup("com.terraformersmc")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${project.property("devauth_version")}")

    implementation(
        "com.github.Noamm9:NoammAddons:${project.property("noammaddons_version")}:${sc.current.version}-${
            project.property(
                "noammaddons_type"
            )
        }@jar"
    )
    compileOnly("gg.essential:${project.property("universalcraft_artifact")}:${project.property("universalcraft_version")}")
    compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
}

tasks.processResources {
    val resourceProperties = mapOf(
        "version" to project.version,
        "minecraft_version" to sc.current.version,
        "loader_version" to project.property("loader_version"),
        "kotlin_loader_version" to project.property("kotlin_loader_version"),
        "noammaddons_version" to project.property("noammaddons_mod_version")
    )
    inputs.properties(resourceProperties)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(resourceProperties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<KotlinCompile>().configureEach {
    inputs.property("stonecutterUsesSharedSources", sc.current.isActive)
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.named<JavaExec>("runClient") {
    classpath = classpath.filter { it.exists() }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename("LICENSE", "LICENSE_${base.archivesName.get()}")
    }
}

tasks.register<Sync>("collectJar") {
    group = "build"
    description = "Collects this Minecraft version's JAR in the root build/libs directory."
    outputs.upToDateWhen { false }
    from(tasks.jar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${sc.current.project}"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}

loom {
    runConfigs.named("client") {
        generateRunConfig.set(true)
        jvmArguments.addAll(
            "-Dmixin.debug.export=true",
            "-Ddevauth.configDir=${rootProject.file(".devauth").absolutePath}",
            "-XX:+AllowEnhancedClassRedefinition",
            "-XX:+IgnoreUnrecognizedVMOptions",
        )
    }

    runConfigs.named("server") {
        generateRunConfig.set(false)
    }
}

afterEvaluate {
    loom.runs.named("client") {
        jvmArguments.add("-javaagent:${configurations.compileClasspath.get().find { "sponge-mixin" in it.name }}")
    }
}