#!/usr/bin/env bash
set -euo pipefail

ROOT="/home/naman/Desktop/Gen_Fin"
NAME="$1"        # e.g. demo-stripe
PKG="$2"         # e.g. stripe
EXTRA_DEP="$3"   # e.g. ":fin-stripe" or "" for none

MODULE_DIR="$ROOT/demo/$NAME"
JAVA_PKG="io.genfin.demo.$PKG"
SRC="$MODULE_DIR/src/main/java/io/genfin/demo/$PKG"
CLASS_NAME="$(echo "$PKG" | sed -r 's/(^|_)([a-z])/\U\2/g')DemoApplication"

mkdir -p "$SRC"

EXTRA_DEP_LINE=""
if [ -n "$EXTRA_DEP" ]; then
  EXTRA_DEP_LINE="    implementation(project(\"$EXTRA_DEP\"))"
fi

cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = providers.gradleProperty("genfin.group").get()
version = providers.gradleProperty("genfin.version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":fin-spring-boot-starter"))
$EXTRA_DEP_LINE
}
EOF

cat > "$SRC/$CLASS_NAME.java" <<EOF
package $JAVA_PKG;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class $CLASS_NAME {

    public static void main(String[] args) {
        SpringApplication.run($CLASS_NAME.class, args);
    }
}
EOF

echo "Scaffolded $NAME ($CLASS_NAME)"
