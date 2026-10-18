plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":fin-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
}
