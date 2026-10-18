plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
    id("java-test-fixtures")
}

dependencies {
    api(project(":fin-api"))
    api(project(":fin-money"))
    api(project(":fin-payment"))

    testFixturesImplementation(project(":fin-money"))
    testFixturesImplementation(project(":fin-payment"))
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.assertj.core)
}
