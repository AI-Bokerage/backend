plugins {
    id("genfin.java-library-conventions")
    id("genfin.testing-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-api"))
    implementation(libs.pdfbox)
}
