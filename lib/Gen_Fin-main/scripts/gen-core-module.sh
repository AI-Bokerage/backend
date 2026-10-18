#!/usr/bin/env bash
# ponytail: one-shot scaffolding script, not meant to run twice on the same module — re-running overwrites by design.
set -euo pipefail

ROOT="/home/naman/Desktop/Gen_Fin"
GROUP_DIR="fin-core"
NAME="$1"        # e.g. fin-money
PKG="$2"         # e.g. money
DEPS_ON_API="$3" # "true" or "false" — false only for fin-api itself

MODULE_DIR="$ROOT/$GROUP_DIR/$NAME"
JAVA_MODULE="io.genfin.$PKG"
SRC="$MODULE_DIR/src/main/java/io/genfin/$PKG"
TEST_SRC="$MODULE_DIR/src/test/java/io/genfin/$PKG"

mkdir -p "$SRC/api" "$SRC/port" "$SRC/internal" "$TEST_SRC"

if [ "$DEPS_ON_API" = "true" ]; then
cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}

dependencies {
    api(project(":fin-api"))
}
EOF
else
cat > "$MODULE_DIR/build.gradle.kts" <<EOF
plugins {
    id("genfin.java-library-conventions")
    id("genfin.publishing-conventions")
}
EOF
fi

REQUIRES_LINE=""
if [ "$DEPS_ON_API" = "true" ]; then
  REQUIRES_LINE="    requires transitive io.genfin.api;"
fi

# ponytail: no `exports` yet — javac rejects exporting a package that holds only
# package-info.java. Add `exports io.genfin.$PKG.api;` / `.port` once real types land.
cat > "$MODULE_DIR/src/main/java/module-info.java" <<EOF
module $JAVA_MODULE {
$REQUIRES_LINE
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
