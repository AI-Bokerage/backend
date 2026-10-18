// Resolves a publish-credential value with Gradle-property precedence over the environment
// variable: a developer's own ~/.gradle/gradle.properties (outside version control) works for
// local publishToMavenLocal/manual releases, falling back to the environment variable CI
// actually sets via secrets. Returns null if neither is set — callers decide how to react to
// that (see the PublishToMavenRepository doFirst check below), rather than this function ever
// substituting a guessed default.
fun Project.resolveCredential(propertyName: String, envName: String): String? =
    findProperty(propertyName) as String? ?: System.getenv(envName)

subprojects {
    repositories { mavenCentral() }
    tasks.withType<Test> { useJUnitPlatform() }
    // Matches CPMS-Platform's own root build.gradle.kts convention. Required for Jackson to
    // deserialize records without explicit @JsonProperty annotations on every constructor
    // parameter — without it, Jackson has no way to know a record constructor parameter's name
    // via reflection, and fails with InvalidDefinitionException. Found this the same way as the
    // Mongo property rename and the Boot 4 module split: from an actual failure, not assumed.
    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    // Shared publish/POM convention for every publishable module. Gated on the java-library
    // plugin specifically, not applied blanket to every subproject: audit-demo applies the
    // Spring Boot application plugin instead (per every prior phase's convention), so it's
    // automatically excluded here with no explicit opt-out needed — confirmed by a required
    // test that audit-demo has no publish-family Gradle task at all.
    plugins.withId("java-library") {
        apply(plugin = "maven-publish")
        group = "com.company.audit"

        // A published library's API documentation is part of the deliverable, not an
        // afterthought — fails the build on any Javadoc *warning* (missing @param, etc.), not
        // only on hard errors, via the standard doclet's "-Xwerror" option.
        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).addStringOption("Xwerror", "-quiet")
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        url.set("https://github.com/your-org/audit-java")
                        licenses {
                            license {
                                // Placeholder — confirm the real license before an actual release.
                                name.set("Apache-2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        organization {
                            // Placeholder — confirm the real organization name/URL before an
                            // actual release.
                            name.set("Company")
                        }
                        developers {
                            developer {
                                // Placeholder — confirm real developer/team info before an
                                // actual release.
                                name.set("Audit Platform Team")
                            }
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "auditJava"
                    // Deliberately never hardcodes a specific vendor's URL (GitHub Packages,
                    // Artifactory, whatever registry CPMS's builds can reach isn't something this
                    // build can know) — resolved via resolveCredential() below. Falls back to an
                    // inert placeholder so merely *configuring* this repository (which Gradle
                    // does for every task, not only publish-related ones) never fails a build
                    // that isn't actually trying to publish remotely — see the doFirst check
                    // below for where an unset URL actually becomes a build failure.
                    url = uri(
                        resolveCredential("audit.java.publish.url", "AUDIT_JAVA_PUBLISH_URL")
                            ?: "https://unset.invalid/audit-java-publish-url-not-configured")
                    // username/password are also placeholder-defaulted, not left null: Gradle's
                    // own PublishToMavenRepository task validates PasswordCredentials eagerly
                    // (failing with a generic "credentials.username doesn't have a configured
                    // value" error before the doFirst check below ever runs) if either is null —
                    // confirmed by an actual failure, not assumed. The doFirst check is what
                    // turns an actually-missing URL into this build's own clear error instead.
                    credentials {
                        username = resolveCredential("audit.java.publish.username", "AUDIT_JAVA_PUBLISH_USERNAME")
                            ?: "unset"
                        password = resolveCredential("audit.java.publish.token", "AUDIT_JAVA_PUBLISH_TOKEN") ?: "unset"
                    }
                }
            }
        }

        // Fails clearly and specifically — not with a generic Gradle stack trace — when
        // AUDIT_JAVA_PUBLISH_URL (or its Gradle-property equivalent) is unset and a task that
        // actually publishes to the remote repository runs. Deliberately scoped to
        // PublishToMavenRepository, Gradle's own task type for exactly that, not to
        // PublishToMavenLocal — publishing to the local .m2 cache never needs a remote
        // credential and must keep working without one.
        tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
            doFirst {
                resolveCredential("audit.java.publish.url", "AUDIT_JAVA_PUBLISH_URL")
                    ?: throw GradleException(
                        "Cannot publish: AUDIT_JAVA_PUBLISH_URL (or Gradle property " +
                            "audit.java.publish.url) is not set. This build never guesses a " +
                            "publish target — set the environment variable (CI) or the Gradle " +
                            "property in your own ~/.gradle/gradle.properties (local) before " +
                            "running './gradlew publish'.")
            }
        }

        // The release process's actual javadoc gate is illustrative in
        // .github/workflows/release.yml, but wiring it here too means a plain
        // `./gradlew publish` run locally enforces the same strictness, not only CI.
        tasks.named("publish") { dependsOn(tasks.named("javadoc")) }
    }
}

// audit-core is a frozen, published module: java-library, zero infra deps. Its build
// configuration is kept here (not inside audit-core/) so later-phase additions never require
// editing anything inside that module. Targets Java 21 to match the real CPMS baseline
// (confirmed against CPMS-Platform's own build.gradle.kts), corrected from an initial
// assumption of Java 17 — see CHANGELOG.md.
project(":audit-core") {
    apply(plugin = "java-library")
    version = "0.4.0"
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }
}
