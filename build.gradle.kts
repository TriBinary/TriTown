plugins {
    kotlin("jvm") version "2.3.10"
    idea
}

group = "net.trilleo.mc.plugins"
version = providers.gradleProperty("plugin_version").get()

idea {
    module {
        isDownloadSources = true
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://repo.glaremasters.me/repository/towny/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

val serverPlugins: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.palmergames.bukkit.towny:towny:${providers.gradleProperty("towny_version").get()}")
    compileOnly("com.github.MilkBowl:VaultAPI:${providers.gradleProperty("vault_api_version").get()}") {
        isTransitive = false
    }
    serverPlugins("com.palmergames.bukkit.towny:towny:${providers.gradleProperty("towny_version").get()}")
    testImplementation(kotlin("test"))
    // Aligned with what Paper 26.2 bundles, since the plugin uses these at runtime through paper-api.
    testImplementation("net.kyori:adventure-api:5.2.0")
    testImplementation("net.kyori:adventure-text-minimessage:5.2.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:5.2.0")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.yaml:snakeyaml:2.2")
}

kotlin {
    jvmToolchain(25)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("projectVersion" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    from(configurations.runtimeClasspath.get().map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["paperweight-mappings-namespace"] = "spigot"
    }
}

// Copies Towny into the test server, replacing any other Towny version left behind by a version bump.
tasks.register<Copy>("copyServerPlugins") {
    val pluginsDir = layout.projectDirectory.dir("run/plugins")
    doFirst { delete(fileTree(pluginsDir) { include("towny-*.jar") }) }
    from(serverPlugins)
    into(pluginsDir)
}

tasks.register<Copy>("copyPlugin") {
    dependsOn("jar", "copyServerPlugins")
    from(tasks.jar.get().archiveFile)
    into(layout.projectDirectory.dir("run/plugins"))
}

tasks.register<JavaExec>("startServer") {
    dependsOn("copyPlugin")
    workingDir(layout.projectDirectory.dir("run"))
    classpath(fileTree(layout.projectDirectory.dir("run")) { include("paper-*.jar") })
    doFirst {
        check(!classpath.isEmpty) { "No paper-*.jar in run/. Download Paper 26.2 from https://papermc.io/downloads/paper into run/." }
    }
    args("--nogui")
    standardInput = System.`in`
}
