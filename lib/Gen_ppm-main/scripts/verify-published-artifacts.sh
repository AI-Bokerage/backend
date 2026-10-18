#!/usr/bin/env bash
# Automates the external-consumer verification procedure documented in
# ppm-core/RELEASE_PROCESS.md — the exact check that caught a real bug in
# Phase 7 (ppm-core's `api` dependencies and the starter's
# `spring-boot-autoconfigure` dependency were published with no version at
# all; every check that resolves via a Gradle project(...) reference within
# this monorepo missed it, because that's a different resolution path than a
# genuinely external consumer uses).
#
# Run this before every release, not just when a dependency changes —
# publishToMavenLocal succeeding is not proof an artifact is consumable.
#
# ponytail: a bash script, not a Gradle plugin or custom task type — the
# actual problem (verify an artifact resolves via Maven coordinates, from
# a directory with zero project(...) references) doesn't need one. Upgrade
# to a proper Gradle task only if this needs to run inside a build (e.g. a
# CI gate wired into `./gradlew check`), not before.
set -euo pipefail

cd "$(dirname "$0")/.."
REPO_ROOT="$(pwd)"
WORKDIR="$(mktemp -d /tmp/ppm-consumer-check.XXXXXX)"
trap 'rm -rf "$WORKDIR"' EXIT

CORE_VERSION=$(grep "^version" ppm-core/build.gradle | head -1 | sed -E "s/version = '(.*)'/\1/")
STARTER_VERSION=$(grep "^version" ppm-spring-boot-starter/build.gradle | head -1 | sed -E "s/version = '(.*)'/\1/")

echo "==> Publishing ppm-core:${CORE_VERSION} and ppm-spring-boot-starter:${STARTER_VERSION} to Maven local"
./gradlew :ppm-core:publishToMavenLocal :ppm-spring-boot-starter:publishToMavenLocal -q

echo "==> Scaffolding a throwaway consumer at ${WORKDIR} (no project(...) reference anywhere)"
mkdir -p "$WORKDIR/src/main/java/com/example/check"

cat > "$WORKDIR/settings.gradle" << EOF
rootProject.name = 'ppm-consumer-check'
EOF

cat > "$WORKDIR/build.gradle" << EOF
plugins { id 'java' }
group = 'com.example'
version = '0.0.1'
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation 'com.company:ppm-spring-boot-starter:${STARTER_VERSION}'
    implementation 'org.springframework.boot:spring-boot-starter:4.0.6'
}
EOF

cat > "$WORKDIR/src/main/java/com/example/check/CheckApp.java" << 'EOF'
package com.example.check;

import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmstarter.PpmAutoConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CheckApp {
    static PlanApplicationService service;
    static PpmAutoConfiguration autoConfig = new PpmAutoConfiguration();

    static Optional<Plan> lookupPlan(PlanRepositoryPort port, UUID id) {
        return port.findById(id);
    }
}
EOF

echo "==> Compiling throwaway consumer against published Maven-local artifacts only"
if (cd "$WORKDIR" && gradle compileJava --console=plain -q); then
    echo "==> PASS: published artifacts are consumable via Maven coordinates alone."
    exit 0
else
    echo "==> FAIL: published artifacts could not be resolved/compiled against by a genuinely external consumer."
    echo "    Check for a dependency missing a version in the published POM — see ppm-core/RELEASE_PROCESS.md."
    exit 1
fi
