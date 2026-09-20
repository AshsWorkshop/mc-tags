import net.ashwork.gradle.multiloader.publication
import net.ashwork.gradle.multiloader.resolveProperty
plugins {
    java
    idea
    // https://projects.neoforged.net/neoforged/moddevgradle
    id("net.neoforged.moddev") version "2.0.147"
    id("multiloader-publishing")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val fullMinecraftVersion = if (minecraftVersion.split(".").size == 2) "${minecraftVersion}.0" else minecraftVersion
version = "${fullMinecraftVersion}.${providers.gradleProperty("${minecraftVersion}_build").get()}"

val GENERATED_DIRECTORY = "generated"
val GENERATED_JAVA = "java"
val GENERATED_SOURCES = "resources"

// Set the toolchain version
java.toolchain.languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java_version").get()))

// Configure vanilla mode
neoForge.neoFormVersion = "${minecraftVersion}-1"

// Setup dynamic sources
sourceSets.main {
    java.srcDir(rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_JAVA).joinToString(File.separator)))
    resources.srcDir(rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_SOURCES).joinToString(File.separator)))
}

// Setup sources jar
java.withSourcesJar()

// Configure all jar tasks
tasks.withType<Jar>().configureEach {
    // Add license
    from(rootDir.resolve("LICENSE")) {
        rename { "META-INF/${it}" }
    }
}

publication {
    name = resolveProperty("mod_name")
}