import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.ashwork.gradle.multiloader.publication
import net.ashwork.gradle.multiloader.resolveProperty
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import javax.xml.stream.events.Namespace

plugins {
    java
    idea
    // https://projects.neoforged.net/neoforged/moddevgradle
    id("net.neoforged.moddev") version "2.0.146"
    id("multiloader-publishing")
}

val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val fullMinecraftVersion = if (minecraftVersion.split(".").size == 2) "${minecraftVersion}.0" else minecraftVersion
version = "${fullMinecraftVersion}.${providers.gradleProperty("${minecraftVersion}_build").get()}"

val BUILD_DIRECTORY = "tags"
val REPORTS_DIRECTORY = "reports"
val GENERATED_DIRECTORY = "generated"
val GENERATED_JAVA = "java"
val GENERATED_SOURCES = "resources"
val SOURCE_METADATA = "metadata"
val INTERSECTION_THRESHOLD = 2

// Configure vanilla mode
neoForge.neoFormVersion = "${minecraftVersion}-1"
neoForge.runs.create("reports") {
    serverData()
    programArguments.addAll("--reports", "--output", rootProject.layout.buildDirectory.dir(REPORTS_DIRECTORY).get().toString())
}

class MinecraftMetadataSupplier : ComponentMetadataSupplier {

    override fun execute(details: ComponentMetadataSupplierDetails) {
        if (details.id.group == "net.fabricmc.fabric-api" && details.id.moduleIdentifier.name == "fabric-api") {
            val minecraftVersion = details.id.version.split("+")[1]
            details.result.setStatus(minecraftVersion)
            details.result.setStatusScheme(listOf(minecraftVersion))
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "NeoForged Maven"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "Fabric Maven"
        url = uri("https://maven.fabricmc.net/")
        setMetadataSupplier(MinecraftMetadataSupplier::class.java)
    }
}

val vanilla = configurations.create("vanilla") {
    resolutionStrategy {
        cacheDynamicVersionsFor(10, TimeUnit.MINUTES)
        cacheChangingModulesFor(10, TimeUnit.MINUTES)
    }
}

dependencies {
    vanilla("net.neoforged:neoforge:${fullMinecraftVersion}.+")
    vanilla("net.fabricmc.fabric-api:fabric-api:latest.${minecraftVersion}")
}

val unpack = tasks.register<Task>("unpackTags") {
    description = "Unpacks the tags from the artifact dependencies."
    group = "tags"

    // Define outputs
    val taskOutput = rootProject.layout.buildDirectory.dir(BUILD_DIRECTORY)
    outputs.dir(taskOutput)

    vanilla.incoming.artifacts.forEach {
        // Define inputs
        inputs.file(it.file)

        // Split component identifier into its directories
        val identifier = it.id.componentIdentifier.displayName
        val fileOutput = taskOutput.map {
            // Convert component identifier into three parts
            it.dir(identifier.substring(0, identifier.lastIndexOf(":")).replace(":", "/"))
        }

        // Copy tags into output
        copy {
            from(zipTree(it.file))
            into(fileOutput)
            include("**/tags/**/*.json")

            includeEmptyDirs = false
        }
    }
}

data class MutablePair<A, B>(var first: A, var second: B)

sealed class TagEntry(open val original: String)
data class ObjectEntry(override val original: String): TagEntry(original) {
    override fun toString(): String = this.original
}
data class ReferenceEntry(override val original: String): TagEntry(original) {
    override fun toString(): String = "#${this.original}"
}

fun getRegistries(slurper: JsonSlurper): Set<String> = ((slurper.parseText(
    rootProject.layout.buildDirectory.dir(REPORTS_DIRECTORY).get().file("reports/datapack.json").asFile.readText()
) as Map<*, *>).get("registries") as Map<*, *>).keys.map { it.toString() }.toSet()

fun determineRegistry(components: List<String>, registries: Set<String>): Int {
    var current: String = ""
    for ((index, path) in components.withIndex()) {
        current += (if (current.isNotEmpty()) "/" else "") + path
        if (registries.contains("minecraft:$current")) {
            return index
        }
    }

    return -1
}

fun String.replaceRefs(replacements: Map<String, String>): String {
    var result = this
    for ((key, value) in replacements) {
        result = result.replace("::${key}::", value)
    }
    return result
}

data class TagMetadata(val registry: String, val namespace: String, val identifier: String) {
    override fun toString(): String = "(${this.registry}) #${this.namespace}:${this.identifier}"
}
fun readTagMetadata(file: File, registries: Set<String>): TagMetadata? {
    val components = file.toPath().map { it.toString() }

    // Validate that this is the tags directory
    if (!components[1].equals("tags")) {
        return null
    }

    // Parse out components
    val namespace = components[0]
    val registryEndIndex = determineRegistry(components.subList(2, components.size), registries) + 2
    val registry = components.subList(2, registryEndIndex + 1).joinToString("/")
    val identifier = components.subList(registryEndIndex + 1, components.size).joinToString("/").run { this.substring(0, this.length - 5) }

    return TagMetadata(registry, namespace, identifier)
}

val computeIntersection = tasks.register<Task>("computeTagIntersection") {
    description = "Computes the intersection from the unpacked tags."
    group = "tags"
    dependsOn(unpack)

    // Define outputs
    val taskOutput = rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_SOURCES).joinToString(File.separator))
    outputs.dir(taskOutput)

    // Define inputs
    val taskInput = rootProject.layout.buildDirectory.dir(BUILD_DIRECTORY)
    inputs.dir(taskInput)

    val slurper = JsonSlurper()

    // Get vanilla registries
    // NOTE: runReports must be executed before this!!!
    val vanillaRegistries = getRegistries(slurper)

    // Create tag mappings
    // registry -> (id -> entry set))
    val tags: MutableMap<String, MutableMap<TagEntry, MutablePair<Int, MutableMap<TagEntry, Int>>>> = mutableMapOf()
    inputs.files.forEach {
        // Get metadata
        val metadata = readTagMetadata(it.relativeTo(taskInput.get().asFile).run {
            val prefix = File(this.toPath().toList().subList(0, 3).joinToString(separator = File.separator))
            this.relativeTo(prefix)
        }, vanillaRegistries)

        // Validate that this is a tag
        if (metadata == null) {
            logger.error("'${it}' is not a valid tag path, skipping.")
            return@forEach
        }

        // Get appropriate map
        val tagEntries = tags.computeIfAbsent(metadata.registry) { _ ->
            mutableMapOf<TagEntry, MutablePair<Int, MutableMap<TagEntry, Int>>>()
        }.computeIfAbsent(ReferenceEntry("${metadata.namespace}:${metadata.identifier}")) { _ ->
            MutablePair(0, mutableMapOf<TagEntry, Int>())
        }
        // Update encounter count
        tagEntries.first = tagEntries.first + 1

        // Read JSON and iterate through values
        ((slurper.parseText(it.readText()) as Map<*, *>).get("values") as List<*>).forEach {
            val id: String = if (it is String) it else (it as Map<*, *>).get("id").toString()
            tagEntries.second.compute(
                if (id.startsWith("#")) ReferenceEntry(id.substring(1)) else ObjectEntry(id)
            ) { _, count ->
                if (count == null) 1 else count + 1
            }
        }
    }

    // Write files to output
    tags.forEach { registry, tags ->
        tags.forEach { tag, entries ->
            // Make sure tag has been seen multiple times
            if (entries.first < INTERSECTION_THRESHOLD) return@forEach

            // Create value list
            val values = mutableListOf<Map<String, *>>()
            entries.second.forEach { entry, count ->
                // Make sure entry has been seen multiple times
                if (count < INTERSECTION_THRESHOLD) return@forEach

                values.add(mapOf(
                    "id" to entry.toString(),
                    // Use required false just in case any non-generated entries appear
                    "required" to false
                ))
            }

            // Write to file
            val identifierComponents = tag.original.split(":")
            val tagFile = taskOutput.map {
                it.file("data/${identifierComponents[0]}/tags/$registry/${identifierComponents[1]}.json").asFile
            }.get()
            Files.createDirectories(tagFile.parentFile.toPath())
            FileWriter(tagFile, StandardCharsets.UTF_8).use {
                it.write(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf<String, Any>(
                    "values" to values
                ))))
            }
        }
    }
}

val generateJavaSources = tasks.register<Task>("generateJavaSources") {
    description = "Generates the java sources for the intersected tags"
    group = "tags"
    dependsOn(computeIntersection)

    // Define outputs
    val taskOutput = rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_JAVA).joinToString(File.separator))
    outputs.dir(taskOutput)

    // Define inputs
    val taskInput = rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_SOURCES).joinToString(File.separator))
    inputs.dir(taskInput)

    val slurper = JsonSlurper()

    // Get vanilla registries
    // NOTE: runReports must be executed before this!!!
    val vanillaRegistries = getRegistries(slurper)

    // Get serialization metadata
    val metadataFile = rootProject.file(listOf(SOURCE_METADATA, "${minecraftVersion}.json").joinToString(File.separator))
    val metadata = (slurper.parseText(metadataFile.readText()) as Map<*, *>).toMutableMap()
    // Populate registry data if not present
    if (!metadata.containsKey("registries")) {
        zipTree(layout.buildDirectory.file("moddev/artifacts/vanilla-${minecraftVersion}-1-sources.jar")).forEach {
            if (it.path.contains((metadata.get("classes") as Map<*, *>).get("Registries").toString().replace(".", File.separator))) {
                data class RegistryMetadata(val identifier: String, val type: String, val reference: String, val imports: List<String>)

                // Read registry file
                val text = it.readText()

                // Get imports
                val imports = Regex("""import ([^\n]+);""").findAll(text).map { it.groupValues[1] }.groupBy { it.substring(it.lastIndexOf(".") + 1) }
                val registries: MutableList<RegistryMetadata> = mutableListOf()

                // Get registry lines
                Regex(metadata.get("ref_from_source_regex").toString()).findAll(text).forEach {
                    // Compute relevant imports
                    val relevantImports: MutableList<String> = mutableListOf()
                    val type = it.groupValues[1]
                    var currentSequence: String = ""
                    type.forEach {
                        if ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".contains(it)) {
                            currentSequence += it
                        } else {
                            if (imports.containsKey(currentSequence)) {
                                relevantImports.addAll(imports.get(currentSequence)!!)
                            }
                            currentSequence = ""
                        }
                    }
                    if (currentSequence.isNotEmpty() && imports.containsKey(currentSequence)) {
                        relevantImports.addAll(imports.get(currentSequence)!!)
                    }

                    registries.add(RegistryMetadata(it.groupValues[3], type, it.groupValues[2], relevantImports))

                }

                metadata["registries"] = registries.associate { Pair(it.identifier, mapOf(
                    "type" to it.type,
                    "reference" to it.reference,
                    "imports" to it.imports
                )) }
                FileWriter(metadataFile, StandardCharsets.UTF_8).use {
                    it.write(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)))
                }
            }
        }
    }

    // Read all tags
    val tagMetadata: MutableMap<String, MutableMap<String, MutableList<TagMetadata>>> = mutableMapOf()
    inputs.files.forEach {
        // Get metadata
        val metadata = readTagMetadata(it.relativeTo(taskInput.get().asFile).run {
            val prefix = File(this.toPath().toList().subList(0, 1).joinToString(separator = File.separator))
            this.relativeTo(prefix)
        }, vanillaRegistries)

        // Validate that this is a tag
        if (metadata == null) {
            logger.error("'${it}' is not a valid tag path, skipping.")
            return@forEach
        }

        tagMetadata.computeIfAbsent(metadata.registry) {
                _ -> mutableMapOf<String, MutableList<TagMetadata>>()
        }.computeIfAbsent(metadata.namespace) {
                _ -> mutableListOf<TagMetadata>()
        }.add(metadata)
    }

    // Generate source files
    val commonClasses = metadata.get("classes") as Map<*, *>

    // Create package path
    val packageName = "${resolveProperty("mod_group")}.${rootProject.name}"
    val packageLocation = taskOutput.map { it.dir(packageName.replace(".", File.separator)) }.get()
    Files.createDirectories(packageLocation.asFile.toPath())

    // Create registry files
    tagMetadata.forEach { registry, tagsByNamespace ->
        val registryReference = (metadata.get("registries") as Map<*, *>).get(registry) as Map<*, *>

        // File name
        val fileName = registryReference.get("reference").toString().lowercase(Locale.ROOT).split("_").map {
            it.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.joinToString("").run { "Common${this}Tags" }

        // Imports
        val imports: MutableList<String> = mutableListOf()

        // Add common imports
        imports.add(commonClasses.get("Registries").toString())
        imports.add(commonClasses.get("Identifier").toString())
        imports.add(commonClasses.get("ResourceKey").toString())
        imports.add(commonClasses.get("TagKey").toString())

        // Add registry-specific imports
        imports.addAll((registryReference.get("imports") as List<*>).map { it.toString() })

        imports.sort()

        // Entries
        val tagEntries: MutableList<String> = mutableListOf()

        // Helper methods
        val helperMethods: MutableList<String> = mutableListOf()

        tagsByNamespace.keys.toList().sorted().forEach {
            // Generate helper methods
            helperMethods.add(metadata.get("helper").toString().replaceRefs(mapOf(
                "Type" to registryReference.get("type").toString(),
                "Method" to it.replace("-", "_").split("_").run {
                    var result = ""
                    for (part in this) {
                        if (result.isEmpty()) result += part
                        else result += part.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    }
                    result
                },
                "RegistryRef" to "Registries.${registryReference.get("reference")}",
                "Namespace" to it
            )))
            helperMethods.add("")

            // Generate tag entries
            tagEntries.addAll(tagsByNamespace[it]!!.sortedBy { it.identifier }.map {
                metadata.get("entry").toString().replaceRefs(mapOf(
                    "Type" to registryReference.get("type").toString(),
                    "Name" to it.identifier.replace("/", "_").uppercase(Locale.ROOT),
                    "Method" to it.namespace.replace("-", "_").split("_").run {
                        var result = ""
                        for (part in this) {
                            if (result.isEmpty()) result += part
                            else result += part.replaceFirstChar { it.titlecase(Locale.ROOT) }
                        }
                        result
                    },
                    "Path" to it.identifier
                ))
            })
            tagEntries.add("")
        }
        helperMethods.removeLast()
        tagEntries.removeLast()

        // Write java file
        FileWriter(packageLocation.file("${fileName}.java").asFile, StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("package ${packageName};")
            writer.appendLine()
            writer.appendLine(imports.map { "import ${it};" }.joinToString("\n"))
            writer.appendLine()
            writer.appendLine("public interface ${fileName} {")
            writer.appendLine()
            tagEntries.forEach {
                writer.appendLine(if (it.isEmpty()) it else "    ${it}")
            }
            writer.appendLine()
            helperMethods.forEach { helper ->
                helper.split("\n").forEach {
                    writer.appendLine(if (it.isEmpty()) it else "    ${it}")
                }
            }
            writer.appendLine("}")
        }
    }
}

fun File.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.readBytes())
    return digest.toHexString()
}

val createFileHashes = tasks.register<Task>("createFileHashes") {
    description = "Creates the hashes for the generated files."
    group = "tags"

    // Define outputs
    val taskOutput = rootProject.file(".generated-hashes/${minecraftVersion}.json")
    outputs.file(taskOutput)

    // Define inputs
    val taskInput = rootProject.layout.buildDirectory.dir(listOf(GENERATED_DIRECTORY, GENERATED_SOURCES).joinToString(File.separator))
    inputs.dir(taskInput)

    // Check file hashes
    var hasNewEntries = false
    val entries = if (taskOutput.exists()) taskOutput.let {
        copy {
            from(it)
            into(rootProject.layout.buildDirectory.dir("compare"))
        }
        mutableMapOf<String, String>(*(JsonSlurper().parse(it) as Map<*, *>).map { Pair(it.key.toString(), it.value.toString()) }.toTypedArray())
    }
    else mutableMapOf<String, String>()
    inputs.files.forEach {
        val relative = it.toRelativeString(taskInput.get().asFile).replace(File.separator, "/")
        val hash = it.md5()
        if (relative !in entries || entries[relative] != hash) {
            hasNewEntries = true
        }
        entries[relative] = hash
    }
    Files.createDirectories(taskOutput.parentFile.toPath())
    FileWriter(taskOutput, StandardCharsets.UTF_8).use { it.write(JsonOutput.prettyPrint(JsonOutput.toJson(entries))) }
}
