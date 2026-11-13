plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.20.1"

// `./gradlew chiseledBuild` builds every supported version in one go.
tasks.register("chiseledBuild") {
    group = "project"
    dependsOn(stonecutter.versions.map { ":${it.project}:build" })
}

// `./gradlew chiseledModrinth` uploads every supported version to Modrinth.
tasks.register("chiseledModrinth") {
    group = "project"
    dependsOn(stonecutter.versions.map { ":${it.project}:modrinth" })
}

// `./gradlew collectJars` puts just the release jars in build/release, for the GitHub release.
tasks.register<Copy>("collectJars") {
    group = "project"
    val target = layout.buildDirectory.dir("release")
    doFirst { delete(target) }
    into(target)
    stonecutter.versions.forEach { version ->
        from(project(":${version.project}").tasks.named("remapJar"))
    }
}
