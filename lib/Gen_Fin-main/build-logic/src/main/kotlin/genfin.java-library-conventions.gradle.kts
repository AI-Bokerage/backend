import java.util.regex.Pattern

plugins {
    id("java-library")
    id("genfin.testing-conventions")
    id("genfin.quality-conventions")
}

group = providers.gradleProperty("genfin.group").get()
version = providers.gradleProperty("genfin.version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.withType<Javadoc>().configureEach {
    val javadocOptions = options as StandardJavadocDocletOptions
    javadocOptions.encoding = "UTF-8"
    javadocOptions.addBooleanOption("Xdoclint:none", true)
    javadocOptions.addBooleanOption("quiet", true)
    // ponytail: no public types exist yet in these foundation modules — real docs land with Money/Invoice/etc.
    isFailOnError = false
    onlyIf {
        source.files.any { file ->
            file.name != "module-info.java"
                && Pattern.compile("\\b(public|protected)\\s+(class|interface|enum|record)\\b")
                    .matcher(file.readText())
                    .find()
        }
    }
}
