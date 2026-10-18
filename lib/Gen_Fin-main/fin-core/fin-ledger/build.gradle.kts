plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-api"))
    api(project(":fin-money"))
    api(project(":fin-payment"))
    api(project(":fin-refund"))
    api(project(":fin-reconciliation"))
}
