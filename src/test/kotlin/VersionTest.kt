import dev.architectury.templates.versionNonNull

fun main() {
    require("1.16.5".versionNonNull > "1.16.4".versionNonNull)
    require("1.16.5+build.16".versionNonNull > "1.16.5+build.15".versionNonNull)
}