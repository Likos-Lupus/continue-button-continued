import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.fabric.loom)
    `maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("continuebuttoncontinued") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    mappings("net.fabricmc:yarn:${libs.versions.yarn.get()}:v2")

    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
}

tasks.named<ProcessResources>("processResources") {
    val modVersion = project.version.toString()
    val minecraftVersion = libs.versions.minecraft.get()
    val loaderVersion = libs.versions.fabric.loader.get()

    inputs.property("version", modVersion)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to modVersion,
                "minecraft_version" to minecraftVersion,
                "loader_version" to loaderVersion
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

java {
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Jar>("jar") {
    val archiveBaseName = providers.gradleProperty("archives_base_name")
    inputs.property("archivesName", archiveBaseName)

    from("LICENSE") {
        rename { "${it}_${archiveBaseName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = providers.gradleProperty("archives_base_name").get()
            from(components["java"])
        }
    }

    repositories {
        // Add your publishing repository here when needed.
    }
}
