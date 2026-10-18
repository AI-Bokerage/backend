plugins {
    alias(libs.plugins.dependency.analysis)
}

allprojects {
    group = providers.gradleProperty("genfin.group").get()
    version = providers.gradleProperty("genfin.version").get()
}
