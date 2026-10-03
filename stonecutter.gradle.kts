plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric" /* [SC] DO NOT EDIT */

tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds and collects every registered version."
    dependsOn(stonecutter.tasks.named("buildAndCollect").map { it.values })
}

tasks.register("chiseledClean") {
    group = "build"
    description = "Cleans every registered version."
    dependsOn(stonecutter.tasks.named("clean").map { it.values })
}

tasks.register("buildActive") {
    group = "build"
    description = "Builds and collects the active version."
    dependsOn(":${stonecutter.current?.project ?: error("No active Stonecutter version")}:buildAndCollect")
}
