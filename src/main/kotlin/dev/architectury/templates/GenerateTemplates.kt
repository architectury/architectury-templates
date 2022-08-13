package dev.architectury.templates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.dom4j.io.SAXReader
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHubBuilder
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun main() {
    val configPath = Paths.get(System.getenv("CONFIG_PATH"))
    val outputPath = Paths.get(System.getenv("OUTPUT_PATH"))
    val publish = System.getenv("PUBLISH").toBooleanStrict()
    outputPath.toFile().mkdirs()
    outputPath.toFile().deleteRecursively()
    outputPath.toFile().mkdirs()
    val config = json.decodeFromString(TemplateConfig.serializer(), Files.readString(configPath))
    val cache = mutableMapOf<String, String>()
    var githubRelease: GHRelease? = null
    if (publish) {
        val github = GitHubBuilder().withOAuthToken(System.getenv("GITHUB_TOKEN")).build()
        val repository = github.getRepository(System.getenv("GITHUB_REPOSITORY"))
        githubRelease = repository.createRelease("release_" + System.getenv("GITHUB_JOB") + "_" + System.currentTimeMillis())
            .name("Architectury Templates")
            .body(Paths.get(System.getenv("BODY_PATH")).toFile().readText() + "\n\n## Downloads\n" +
                    config.versions.entries.joinToString("\n") { (id, entry) ->
                        "- $id.zip: ${entry.description}"
                    })
            .create()
    }
    config.versions.forEach { (id, entry) ->
        val outputZip = outputPath.resolve("$id.zip")
        val outputFolder = outputPath.resolve(id)
        outputFolder.toFile().deleteRecursively()
        println()
        println()
        println("Handling $id")
        val toTransform = transformTokens(config, entry, cache)
        val replacer = createReplacer(toTransform)
        ZipOutputStream(Files.newOutputStream(outputZip)).use { zipOutputStream ->
            val entries = mutableMapOf<String, ByteArray>()
            entry.templates.map { Paths.get(it) }.forEach { templateDirPath ->
                Files.walk(templateDirPath).filter { Files.isRegularFile(it) }.forEach { path ->
                    val pathName = templateDirPath.relativize(path).toString()
                    if (pathName.isTextFile) {
                        entries[replacer(pathName).first] = Files.readString(path).let { originalText ->
                            val (newText, matchedNotReplaced) = replacer(originalText)
                            if (matchedNotReplaced.isNotEmpty()) {
                                System.err.println("Not replaced: $pathName")
                                matchedNotReplaced.forEach {
                                    System.err.println("\t${it}")
                                }
                            }
                            newText
                        }.encodeToByteArray()
                    } else {
                        entries[replacer(pathName).first] = Files.readAllBytes(path)
                    }
                }
            }
            entries.forEach { (path, bytes) ->
                zipOutputStream.putNextEntry(ZipEntry(path))
                zipOutputStream.write(bytes)
                val outsidePath = outputFolder.resolve(path)

                if (outsidePath.parent != null) {
                    Files.createDirectories(outsidePath.parent)
                }

                Files.write(outsidePath, bytes)
            }
        }
        githubRelease?.uploadAsset(outputZip.toFile(), "application/zip")
    }
}

fun createReplacer(toTransform: Map<String, String>): (String) -> Pair<String, List<String>> = { str ->
    var out = str
    toTransform.forEach { (from, to) ->
        out = out.replace(from, to)
    }
    val matchedNotReplaced = "@([A-Z_]+)@".toRegex().findAll(out).toMutableList()
    matchedNotReplaced.removeIf { result ->
        val (token) = result.destructured
        if (token.startsWith("__")) {
            println("Replacing ${result.value} with ")
            out = out.replace(result.value, "")
            true
        } else {
            false
        }
    }
    out to matchedNotReplaced.map { it.value }
}

fun transformTokens(config: TemplateConfig, entry: TemplateEntry, cache: MutableMap<String, String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val inherited = mutableSetOf<String>()

    map["@PACKAGE@"] = System.getenv("PACKAGE")
    map["@MODID@"] = System.getenv("MODID")

    fun inherit(inheritToken: String) {
        if (inherited.add(inheritToken)) {
            val inheritConfig = config.versions[inheritToken]!!
            inheritConfig.tokens.forEach { (token, element) ->
                val replacement = element.findReplacement(config, cache)
                map.putIfAbsent("@$token@", replacement)
            }
            inheritConfig.inherit_tokens.forEach { inherit(it) }
        }
    }
    
    entry.inherit_tokens.forEach { inherit(it) }
    entry.tokens.forEach { (token, element) ->
        val replacement = element.findReplacement(config, cache)
        map["@$token@"] = replacement
    }
    map.toMutableMap().forEach { (key, value) ->
        map.entries.forEach { entry ->
            entry.setValue(entry.value.replace(key, value))
        }
    }
    map.forEach { (from, to) ->
        println("Replacing $from with $to")
    }
    return map
}

private val String.isTextFile: Boolean
    get() = listOf(".txt", ".gradle", ".java", ".kt", ".kts", ".gradle", ".groovy", ".properties", ".json", ".mcmeta", ".cfg", ".toml", ".yaml").any { ext ->
        endsWith(ext)
    }

private fun JsonElement.findReplacement(config: TemplateConfig, cache: MutableMap<String, String>): String {
    if (this is JsonPrimitive) {
        if (this.content.startsWith("#")) {
            return (config.global_tokens[this.content.substring(1)] ?: throw NullPointerException("Failed to find global token: ${this.content.substring(1)}"))
                .findReplacement(config, cache)
        }
        return this.content
    } else if (this is JsonObject) {
        val url = this["pom"]!!.jsonPrimitive.content
        val filter = this["filter"]!!.jsonPrimitive.content.toRegex()
        val content = cache.getOrPut(url) { URL(url).readText() }
        return SAXReader().read(content.reader()).rootElement
            .element("versioning")
            .element("versions")
            .elementIterator("version")
            .asSequence()
            .filter { it.text.matches(filter) }
            .mapNotNull { it.text.version }
            .maxOrNull()?.version ?: throw NullPointerException("Failed to find version, with filter $filter from $url")
    }
    throw IllegalArgumentException("Unknown element type: $this")
}

@Serializable
data class TemplateConfig(
    val global_tokens: Map<String, JsonElement>,
    val versions: Map<String, TemplateEntry>,
)

@Serializable
data class TemplateEntry(
    val templates: List<String>,
    val description: String,
    val inherit_tokens: List<String> = emptyList(),
    val tokens: Map<String, JsonElement>,
)

val String.version: Version?
    get() {
        return try {
            versionNonNull
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

val String.versionNonNull: Version
    get() = Version(this)

@JvmInline
value class Version(val version: String) : Comparable<Version> {
    init {
        require(version.matches("(\\d+(?:\\.\\d+)*)-?((?:[^+]*)?)(?:\\+(.*))?".toRegex())) { "Invalid version format" }
    }

    data class VersionParts(
        val version: String,
        val snapshot: String,
        val metadata: String,
    )

    val parts: VersionParts
        get() {
            return "(\\d+(?:\\.\\d+)*)-?((?:[^+]*)?)(?:\\+(.*))?".toRegex().matchEntire(version)!!.let {
                val (version, snapshot, metadata) = it.destructured
                VersionParts(version, snapshot, metadata)
            }
        }

    override operator fun compareTo(other: Version): Int {
        val (thisVersion, thisSnapshot, thisMetadata) = parts
        val (otherVersion, otherSnapshot, otherMetadata) = other.parts
        val compare = compare(thisVersion, otherVersion)
        if (compare != 0) {
            return compare
        }
        return if (thisSnapshot == "") {
            if (otherSnapshot == "") {
                compare(thisMetadata, otherMetadata)
            } else 1
        } else if (otherSnapshot == "") {
            -1
        } else {
            compare(thisSnapshot, otherSnapshot)
        }
    }

    fun compare(first: String, second: String): Int {
        if (first.isEmpty() || second.isEmpty()) return 0
        fun String.toIntSafe(): Int = toIntOrNull() ?: 0
        val thisParts = first.split('.').toTypedArray()
        val thatParts = second.split('.').toTypedArray()
        val length = max(thisParts.size, thatParts.size)
        for (i in 0 until length) {
            val thisPart = if (i < thisParts.size) thisParts[i].toIntSafe() else 0
            val thatPart = if (i < thatParts.size) thatParts[i].toIntSafe() else 0
            if (thisPart < thatPart) return -1
            if (thisPart > thatPart) return 1
        }
        return 0
    }
}
