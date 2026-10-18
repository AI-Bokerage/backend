plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-provider-api"))
    implementation(libs.razorpay.java)
    testImplementation(testFixtures(project(":fin-provider-api")))
}
