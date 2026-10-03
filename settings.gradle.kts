pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version
            providers.gradleProperty("stonecutter_version").get()
    id("dev.kikugie.loom-back-compat") version
            providers.gradleProperty("loom_back_compat_version").get() apply
            false
    // Declared here (apply false) so loom-back-compat can apply the correct Architectury Loom
    // variant from a single shared plugin classloader.
    id("dev.architectury.loom-no-remap") version
            providers.gradleProperty("architectury_loom_version").get() apply
            false
    id("dev.architectury.loom-remap") version
            providers.gradleProperty("architectury_loom_version").get() apply
            false
}

stonecutter {
    create(rootProject) {
        fun target(version: String) {
            version("$version-fabric", version).buildscript("build.fabric.gradle.kts")
            version("$version-neoforge", version).buildscript("build.neoforge.gradle.kts")
        }

        target("26.1.2")
        target("26.2")
        target("26.3")

        vcsVersion = "26.3-fabric"
    }
}

// Architectury Loom selects its platform from the `loom.platform` project property, which must be
// present before the Loom plugin is applied. Derive it from the Stonecutter node name suffix.
gradle.beforeProject {
    val loader = path.substringAfterLast('-')
    if (loader == "fabric" || loader == "neoforge") {
        extensions.extraProperties.set("loom.platform", loader)
    }
}

rootProject.name = "continue-button-continued"
