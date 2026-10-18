import java.time.Instant

plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}

tasks.processResources {
    val buildTimestamp = Instant.now().toString()
    val frameworkVersion = project.version.toString()
    inputs.property("buildTimestamp", buildTimestamp)
    filesMatching("**/version.properties") {
        expand(mapOf("version" to frameworkVersion, "buildTimestamp" to buildTimestamp))
    }
}
