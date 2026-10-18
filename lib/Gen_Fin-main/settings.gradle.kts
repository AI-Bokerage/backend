pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gen-fin"

fun module(path: String, dir: String) {
    include(path)
    project(path).projectDir = file(dir)
}

// fin-core
module(":fin-api", "fin-core/fin-api")
module(":fin-money", "fin-core/fin-money")
module(":fin-tax", "fin-core/fin-tax")
module(":fin-invoice", "fin-core/fin-invoice")
module(":fin-payment", "fin-core/fin-payment")
module(":fin-provider-api", "fin-core/fin-provider-api")
module(":fin-refund", "fin-core/fin-refund")
module(":fin-reconciliation", "fin-core/fin-reconciliation")
module(":fin-ledger", "fin-core/fin-ledger")
module(":fin-pricing", "fin-core/fin-pricing")
module(":fin-dunning", "fin-core/fin-dunning")
module(":fin-document", "fin-core/fin-document")
module(":fin-events", "fin-core/fin-events")

// fin-providers
module(":fin-stripe", "fin-providers/fin-stripe")
module(":fin-razorpay", "fin-providers/fin-razorpay")

// spring-starter
module(":fin-autoconfigure", "spring-starter/fin-autoconfigure")
module(":fin-spring-boot-starter", "spring-starter/fin-spring-boot-starter")

// demo
module(":demo-basic", "demo/demo-basic")
module(":demo-stripe", "demo/demo-stripe")
module(":demo-razorpay", "demo/demo-razorpay")
module(":demo-invoice", "demo/demo-invoice")

// bom
include(":bom")

// integration tests
include(":integration-tests")
