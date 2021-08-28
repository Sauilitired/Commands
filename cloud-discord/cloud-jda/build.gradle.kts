dependencies {
    api(project(":cloud-core"))
    compileOnly(project(":cloud-annotations"))
    compileOnly("net.dv8tion", "JDA", Versions.jda)
}
