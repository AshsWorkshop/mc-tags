import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.ashwork.gradle.multiloader.publication
import net.ashwork.gradle.multiloader.resolveProperty
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import javax.xml.stream.events.Namespace

plugins {
    `java-platform`
    id("multiloader-publishing")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val fullMinecraftVersion = if (minecraftVersion.split(".").size == 2) "${minecraftVersion}.0" else minecraftVersion
version = "${fullMinecraftVersion}.${providers.gradleProperty("${minecraftVersion}_build").get()}"

dependencies {
    constraints {
        subprojects.forEach { if (!it.name.contains("generator")) api(it) }
    }
}

publishing.publications.create<MavenPublication>("platform") {
    from(components["javaPlatform"])
}
