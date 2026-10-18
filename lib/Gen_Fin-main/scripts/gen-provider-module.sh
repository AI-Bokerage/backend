#!/usr/bin/env bash
set -euo pipefail

ROOT="/home/naman/Desktop/Gen_Fin"
NAME="$1"  # e.g. fin-stripe
PKG="$2"   # e.g. stripe

MODULE_DIR="$ROOT/fin-providers/$NAME"
JAVA_MODULE="io.genfin.$PKG"
SRC="$MODULE_DIR/src/main/java/io/genfin/$PKG"

mkdir -p "$SRC/api" "$SRC/port" "$SRC/internal"

cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-provider-api"))
}
EOF

# ponytail: no `exports` yet — javac rejects exporting a package that holds only
# package-info.java. Add `exports io.genfin.$PKG.api;` / `.port` once real types land.
cat > "$MODULE_DIR/src/main/java/module-info.java" <<EOF
module $JAVA_MODULE {
    requires transitive io.genfin.providerapi;
}
EOF

cat > "$SRC/api/package-info.java" <<EOF
/** Public API of the {@code $JAVA_MODULE} module. */
package io.genfin.$PKG.api;
EOF

cat > "$SRC/port/package-info.java" <<EOF
/** Extension points (SPI) of the {@code $JAVA_MODULE} module. */
package io.genfin.$PKG.port;
EOF

cat > "$SRC/internal/package-info.java" <<EOF
/** Internal implementation of the {@code $JAVA_MODULE} module — not exported. */
package io.genfin.$PKG.internal;
EOF

echo "Scaffolded $NAME ($JAVA_MODULE)"
