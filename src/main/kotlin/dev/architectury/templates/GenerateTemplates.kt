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
        githubRelease = repository.createRelease("release_" + System.getenv("GITHUB_JOB"))
            .name("Architectury Templates")
            .body(Paths.get(System.getenv("BODY_PATH")).toFile().readText())
            .create()
    }
    config.versions.forEach { (id, entry) ->
        val outputZip = outputPath.resolve("$id.zip")
        val toTransform = transformTokens(config, entry, cache)
        ZipOutputStream(Files.newOutputStream(outputZip)).use { zipOutputStream ->
            val entries = mutableMapOf<String, ByteArray>()
            entry.templates.map { Paths.get(it) }.forEach { templateDirPath ->
                Files.walk(templateDirPath).filter { Files.isRegularFile(it) }.forEach { path ->
                    val pathName = templateDirPath.relativize(path).toString()
                    if (pathName.isTextFile) {
                        entries[pathName] = Files.readString(path).let {
                            var out = it
                            toTransform.forEach { (from, to) ->
                                out = out.replace(from, to)
                            }
                            out
                        }.encodeToByteArray()
                    } else {
                        entries[pathName] = Files.readAllBytes(path)
                    }
                }
            }
            entries.forEach { (path, bytes) ->
                zipOutputStream.putNextEntry(ZipEntry(path))
                zipOutputStream.write(bytes)
            }
        }
        githubRelease?.uploadAsset(outputZip.toFile(), "application/zip")
    }
}

fun transformTokens(config: TemplateConfig, entry: TemplateEntry, cache: MutableMap<String, String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    entry.inherit_tokens?.let { config.versions[it]!!.tokens }?.forEach { (token, element) ->
        val replacement = element.findReplacement(config, cache)
        println("Replacing @$token@ with $replacement")
        map["@$token@"] = replacement
    }
    entry.tokens.forEach { (token, element) ->
        val replacement = element.findReplacement(config, cache)
        println("Replacing @$token@ with $replacement")
        map["@$token@"] = replacement
    }
    return map
}

private val String.isTextFile: Boolean
    get() = listOf(".txt", ".gradle", ".java", ".kt", ".kts", ".gradle", ".groovy", ".properties", ".json").any { ext ->
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
    val inherit_tokens: String? = null,
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
        require(version.matches("(\\d+(?:\\.\\d+)*)-?((?:.*)?)\\+?((?:.*)?)".toRegex())) { "Invalid version format" }
    }

    data class VersionParts(
        val version: String,
        val snapshot: String,
        val metadata: String,
    )

    val parts: VersionParts
        get() {
            return "(\\d+(?:\\.\\d+)*)-?((?:.*)?)\\+?((?:.*)?)".toRegex().matchEntire(version)!!.let {
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
