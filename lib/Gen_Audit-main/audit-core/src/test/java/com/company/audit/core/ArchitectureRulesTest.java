package com.company.audit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if any main-source class in this module imports an infrastructure or framework
 * dependency, enforcing the "zero framework/infra dependencies" constraint at build time rather
 * than by convention alone.
 */
class ArchitectureRulesTest {

    private static final List<Pattern> FORBIDDEN_IMPORTS = List.of(
            Pattern.compile("^import\\s+org\\.springframework\\..*"),
            Pattern.compile("^import\\s+com\\.rabbitmq\\..*"),
            Pattern.compile("^import\\s+com\\.mongodb\\..*"),
            Pattern.compile("^import\\s+java\\.sql\\..*"),
            Pattern.compile("^import\\s+javax\\.persistence\\..*"),
            Pattern.compile("^import\\s+jakarta\\.persistence\\..*"),
            Pattern.compile("^import\\s+org\\.hibernate\\..*"),
            Pattern.compile("^import\\s+reactor\\..*"),
            Pattern.compile("^import\\s+org\\.apache\\.kafka\\..*"));

    @Test
    void mainSourceContainsNoForbiddenImports() throws IOException {
        Path mainSourceRoot = mainSourceRoot();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(mainSourceRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> checkFile(p, violations));
        }

        assertThat(violations).isEmpty();
    }

    private void checkFile(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.strip();
                for (Pattern pattern : FORBIDDEN_IMPORTS) {
                    if (pattern.matcher(trimmed).matches()) {
                        violations.add(path + ": " + trimmed);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path mainSourceRoot() {
        // Gradle's Test task runs with the project directory as the working directory.
        return Path.of(System.getProperty("user.dir")).resolve("src/main/java");
    }
}
