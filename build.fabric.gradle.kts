plugins {
    id("dev.kikugie.loom-back-compat")
}

val modId = providers.gradleProperty("archives_base_name").get()
val modVersion = providers.gradleProperty("mod_version").get()
val mcVersion = sc.current.version

val fabricApiVersion = when (mcVersion) {
    "26.1.2" -> "0.155.3+26.1.2"
    "26.2" -> "0.161.0+26.2"
    "26.3" -> "0.161.0+26.3"
    else -> error("No Fabric API version configured for Minecraft $mcVersion")
}

val architecturyVersion = when (mcVersion) {
    "26.1.2" -> "20.1.16"
    "26.2" -> "21.1.11"
    "26.3" -> "22.0.3"
    else -> error("No Architectury API version configured for Minecraft $mcVersion")
}

group = providers.gradleProperty("maven_group").get()
version = "$modVersion+$mcVersion"
base.archivesName.set("$modId-fabric")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://maven.architectury.dev/") { name = "Architectury" }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    loomx.applyMojangMappings()

    modImplementation(libs.fabric.loader)
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("dev.architectury:architectury-fabric:$architecturyVersion")
}

loom {
    runConfigs.configureEach {
        runDir(rootProject.file("run").path)
    }
}

sourceSets["main"].apply {
    java.srcDir(rootProject.file("src/fabric/java"))
    resources.srcDir(rootProject.file("src/fabric/resources"))
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
        "loader_version" to libs.versions.fabric.loader.get(),
        "architectury_version" to architecturyVersion,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
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
    description = "Builds the Fabric jar and copies it into the root build/libs directory."
    dependsOn(tasks.named("build"))
    from(loomx.modJar, loomx.modSourcesJar)
    into(rootProject.layout.buildDirectory.dir("libs/${sc.current.project}"))
}
