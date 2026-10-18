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
    api(project(":fin-api"))
    api(project(":fin-money"))
    api(project(":fin-invoice"))
    api(project(":fin-payment"))
    api(project(":fin-provider-api"))
    api(project(":fin-refund"))
    api(project(":fin-reconciliation"))
    api(project(":fin-ledger"))
    api(project(":fin-pricing"))
    api(project(":fin-dunning"))
    api(project(":fin-document"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Optional providers: compile-time only, so fin-stripe/fin-razorpay never leak to consumers
    // that don't add them explicitly. @ConditionalOnClass gates activation at runtime.
    compileOnly(project(":fin-stripe"))
    compileOnly(project(":fin-razorpay"))
    compileOnly("org.springframework.boot:spring-boot-actuator")

    testImplementation(project(":fin-stripe"))
    testImplementation(project(":fin-razorpay"))
    testImplementation("org.springframework.boot:spring-boot-actuator")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}
