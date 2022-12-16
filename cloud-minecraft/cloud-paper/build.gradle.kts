plugins {
    id("cloud.base-conventions")
}

dependencies {
    api(projects.cloudBukkit)
    compileOnly(projects.cloudMinecraftExtras)
    compileOnly(libs.paperApi)
    compileOnly(libs.paperMojangApi)
    compileOnly(libs.jetbrainsAnnotations)
    compileOnly(libs.guava)
}
