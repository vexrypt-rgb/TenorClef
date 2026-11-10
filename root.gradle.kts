plugins {
    id("fabric-loom") version "1.15.5" apply false
    id("com.replaymod.preprocess") version "c2041a34ae"
}

subprojects {
    repositories {
        mavenCentral()
        maven("https://libraries.minecraft.net/")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        maven("https://jitpack.io")
    }
}

// Ostinato: versions/mapping-26.3-1.21.11.txt (26.3 Mojang names -> 1.21.11 Yarn names) is not committed, because it is
// derived from Mojang's names. When it is missing it is fetched once from the TenorClef commit where it was last
// edited, checked against its SHA-256, and kept untracked (.gitignore). Edit the local copy freely; to fetch from
// somewhere else pass -Ptc.mapping263.url=<url> (no checksum check then). See docs/DEVELOPMENT.md.
val mapping263 = file("versions/mapping-26.3-1.21.11.txt")
if (!mapping263.isFile && !providers.gradleProperty("no26").isPresent) {
    val custom = providers.gradleProperty("tc.mapping263.url").orNull
    val url = custom ?: "https://raw.githubusercontent.com/vexrypt-rgb/TenorClef/01faac19f93ea1407502a37512130a2cd1189174/versions/mapping-26.3-1.21.11.txt"
    val bytes = java.net.URI(url).toURL().readBytes()
    val got = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    if (custom == null && got != "f591058266141045fec2677a8c5d41500e67c52a3e4230a15eb71edae3924398") {
        throw GradleException("mapping-26.3-1.21.11.txt from $url has SHA-256 $got, expected f5910582...")
    }
    mapping263.writeBytes(bytes)
    logger.lifecycle("Fetched ${mapping263.name} from $url")
}
// The preprocess chain is a LINKED LIST and must stay complete.
// PreprocessPlugin.apply() calls parent.extensions.getByType<RootPreprocessExtension>()
// for every non-root project, so each version module requires its PARENT to have run
// this block. Trimming the chain to speed up a 1.16.1-only build throws
// NullPointerException at PreprocessPlugin.apply(PreprocessPlugin.kt:60) — verified.
preprocess {
    // 26.3 heads the chain by default; -Pno26 leaves it out (see settings.gradle.kts).
    val with26 = !providers.gradleProperty("no26").isPresent
    val mc260300 = if (with26) createNode("26.3", 260300, "mojmap") else null
    val mc12111 = createNode("1.21.11", 12111, "yarn")
    val mc12104 = createNode("1.21.4", 12104, "yarn")
    val mc12101 = createNode("1.21.1", 12101, "yarn")
    val mc12100 = createNode("1.21", 12100, "yarn")
    val mc12006 = createNode("1.20.6", 12006, "yarn")
    val mc12005 = createNode("1.20.5", 12005, "yarn")
    val mc12004 = createNode("1.20.4", 12004, "yarn")
    val mc12002 = createNode("1.20.2", 12002, "yarn")
    val mc12001 = createNode("1.20.1", 12001, "yarn")
    val mc11904 = createNode("1.19.4", 11904, "yarn")
    val mc11802 = createNode("1.18.2", 11802, "yarn")
    val mc11800 = createNode("1.18", 11800, "yarn")
    val mc11701 = createNode("1.17.1", 11701, "yarn")
    val mc11605 = createNode("1.16.5", 11605, "yarn")
    val mc11601 = createNode("1.16.1", 11601, "yarn")

    mc260300?.link(mc12111, file("versions/mapping-26.3-1.21.11.txt"))
    mc12111.link(mc12104)
    mc12104.link(mc12101)
    mc12101.link(mc12100)
    mc12100.link(mc12006)
    mc12006.link(mc12005)
    mc12005.link(mc12004)
    mc12004.link(mc12002)
    mc12002.link(mc12001)
    mc12001.link(mc11904)
    mc11904.link(mc11802, file("versions/mapping-1.19.4-1.18.2.txt"))
    mc11802.link(mc11800)
    mc11800.link(mc11701, file("versions/mapping-1.18.2-1.17.1.txt"))
    mc11701.link(mc11605, file("versions/mapping-1.17.1-1.16.5.txt"))
    mc11605.link(mc11601, file("versions/mapping-1.16.5-1.16.1.txt"))
}