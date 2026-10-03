plugins {
    id("dev.kikugie.loom-back-compat")
}

val modId = providers.gradleProperty("archives_base_name").get()
val modVersion = providers.gradleProperty("mod_version").get()
val mcVersion = sc.current.version

val neoForgeVersion = when (mcVersion) {
    "26.1.2" -> "26.1.2.114"
    "26.2" -> "26.2.0.88"
    "26.3" -> "26.3.0.46-beta"
    else -> error("No NeoForge version configured for Minecraft $mcVersion")
}

val architecturyVersion = when (mcVersion) {
    "26.1.2" -> "20.1.16"
    "26.2" -> "21.1.11"
    "26.3" -> "22.0.3"
    else -> error("No Architectury API version configured for Minecraft $mcVersion")
}

group = providers.gradleProperty("maven_group").get()
version = "$modVersion+$mcVersion"
base.archivesName.set("$modId-neoforge")

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    maven("https://maven.architectury.dev/") { name = "Architectury" }
}

// Architectury Loom registers the `neoForge` configuration lazily, so the loader dependency is
// attached as soon as the configuration appears.
val dependencyHandler = dependencies
configurations.matching { it.name == "neoForge" }.configureEach {
    dependencyHandler.add(
        name,
        "net.neoforged:neoforge:$neoForgeVersion"
    )
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()

    modImplementation("dev.architectury:architectury-neoforge:$architecturyVersion")
}

loom {
    runConfigs.configureEach {
        runDir(rootProject.file("run").path)
    }
}

sourceSets["main"].apply {
    java.srcDir(rootProject.file("src/neoforge/java"))
    resources.srcDir(rootProject.file("src/neoforge/resources"))
}

java {
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(libs.versions.java.get().toInt())
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to mcVersion,
        "neoforge_version" to neoForgeVersion,
        "architectury_version" to architecturyVersion,
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$modId" }
    }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the NeoForge jar and copies it into the root build/libs directory."
    dependsOn(tasks.named("build"))
    from(loomx.modJar, loomx.modSourcesJar)
    into(rootProject.layout.buildDirectory.dir("libs/${sc.current.project}"))
}
