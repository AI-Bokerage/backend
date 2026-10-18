plugins {
    id("java-platform")
    id("maven-publish")
}

group = providers.gradleProperty("genfin.group").get()
version = providers.gradleProperty("genfin.version").get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it != project && it.name != "integration-tests" && !it.path.startsWith(":demo") }
            .forEach { api(it) }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
