plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:${libs.versions.spotless.get()}")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${libs.versions.errorprone.plugin.get()}")
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:${libs.versions.dependency.analysis.get()}")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}")
    implementation("io.spring.gradle:dependency-management-plugin:${libs.versions.spring.dependency.management.get()}")
}
