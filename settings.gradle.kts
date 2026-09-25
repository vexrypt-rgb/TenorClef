import java.util.Properties

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.replaymod.preprocess" -> {
                    useModule("com.github.replaymod:preprocessor:${requested.version}")
                }
            }
        }
    }
}

// Optional personal overrides (gitignored). Prefer JAVA_HOME for the JDK.
// org.gradle.java.home in this file is applied as a project property only —
// for daemon JVM selection put it in ~/.gradle/gradle.properties or set JAVA_HOME.
val localPropsFile = file("gradle.properties.local")
if (localPropsFile.exists()) {
    val localProps = Properties()
    localPropsFile.reader().use { localProps.load(it) }
    localProps.forEach { (rawKey, rawValue) ->
        val key = rawKey.toString()
        val value = rawValue.toString()
        extra[key] = value
        if (key == "org.gradle.java.home") {
            println(
                "[tenorclef] gradle.properties.local sets org.gradle.java.home — " +
                    "Gradle selects the daemon JDK from JAVA_HOME or ~/.gradle/gradle.properties; " +
                    "export JAVA_HOME=\"$value\" if this pin is required."
            )
        }
    }
    println("[tenorclef] Loaded ${localProps.size} entries from gradle.properties.local")
}

rootProject.name = "altoclef"
rootProject.buildFileName = "root.gradle.kts"

// Full remap chain must be included for preprocess (even if we mostly build 1.16.1 / 1.21.11).
//
// NOTE: the chain CANNOT be trimmed to speed up a single-version build.
// PreprocessPlugin.apply() does parent.extensions.getByType<RootPreprocessExtension>()
// for every non-root project, so each version module requires its PARENT project to
// have run the root `preprocess {}` block. Dropping ancestors => NullPointerException
// at PreprocessPlugin.apply(PreprocessPlugin.kt:60). Verified on 1.16.1 both with a
// lone node and with a trimmed two-node chain.
//
// If 16-minute builds become intolerable, prefer REDUCING BUILD COUNT (batch edits,
// javap-verify before building, longer runs between builds) over trimming the graph.
val versions = listOf(
    "1.21.11",
    "1.21.1",
    "1.21",
    "1.20.6",
    "1.20.5",
    "1.20.4",
    "1.20.2",
    "1.20.1",
    "1.19.4",
    "1.18.2",
    "1.18",
    "1.17.1",
    "1.16.5",
    "1.16.1"
)

versions.forEach { version ->
    // Gradle 9+ refuses include() when projectDir is missing (clean CI / fresh clone).
    // Preprocess fills sources later; empty dirs are enough to configure.
    val versionDir = file("versions/$version")
    if (!versionDir.exists()) {
        versionDir.mkdirs()
    }
    include(":$version")
    project(":$version").apply {
        projectDir = versionDir
        buildFileName = "../../build.gradle"
        name = version
    }
}
