package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforced across every package in this starter — {@code api}/{@code port},
 * {@code ingestion}, {@code wire}, {@code messaging}, {@code verification}, and both
 * {@code persistence} adapter packages included, since this test walks the entire
 * {@code src/main/java} tree rather than an enumerated package list.
 */
class ArchitectureRulesTest {

    private static final Pattern FORBIDDEN_CORE_INTERNAL_IMPORT =
            Pattern.compile("^import\\s+com\\.company\\.audit\\.core\\.internal\\..*");

    private static final String JPA_PACKAGE_SEGMENT = "com.company.audit.spring.persistence.jpa";
    private static final String MONGO_PACKAGE_SEGMENT = "com.company.audit.spring.persistence.mongo";
    private static final String VERIFICATION_PACKAGE_SEGMENT = "com.company.audit.spring.verification";
    private static final String MESSAGING_PACKAGE_SEGMENT = "com.company.audit.spring.messaging";
    private static final String ANCHOR_PACKAGE_SEGMENT = "com.company.audit.spring.anchor";
    private static final String SCHEDULING_PACKAGE_SEGMENT = "com.company.audit.spring.scheduling";
    private static final String METRICS_PACKAGE_SEGMENT = "com.company.audit.spring.metrics";

    /**
     * This is the first real exercise of Phase 1's {@code module-info.java} boundary against a
     * genuine external consumer: {@code audit-spring-boot-starter} may depend only on
     * {@code audit-core}'s {@code api} and {@code port} packages.
     */
    @Test
    void noClassImportsAuditCoreInternalPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> checkForForbiddenImport(path, FORBIDDEN_CORE_INTERNAL_IMPORT, violations));
        assertThat(violations).isEmpty();
    }

    /**
     * {@code persistence.jpa} and {@code persistence.mongo} are separate adapters for separate
     * ports ({@code ChainRepository} and {@code EventStore}); neither may depend on the other's
     * internals, matching the same isolation discipline already enforced against
     * {@code audit-core}'s {@code internal} package.
     */
    @Test
    void jpaAndMongoPersistencePackagesNeverImportEachOther() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> checkNoMutualImport(
                path, "/persistence/jpa/", JPA_PACKAGE_SEGMENT, "/persistence/mongo/", MONGO_PACKAGE_SEGMENT, violations));
        assertThat(violations).isEmpty();
    }

    /**
     * {@code verification} (scheduled chain verification) and {@code messaging} (the Rabbit
     * consumer) are independent, unrelated concerns — neither may depend on the other's
     * internals, same isolation discipline as the persistence adapters above.
     */
    @Test
    void verificationAndMessagingPackagesNeverImportEachOther() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> checkNoMutualImport(
                path,
                "/verification/",
                VERIFICATION_PACKAGE_SEGMENT,
                "/messaging/",
                MESSAGING_PACKAGE_SEGMENT,
                violations));
        assertThat(violations).isEmpty();
    }

    /**
     * {@code anchor} (scheduled anchor publishing) is an independent, unrelated concern from
     * either persistence adapter or from {@code messaging} — neither may depend on the other's
     * internals, same isolation discipline as the other pairs above.
     *
     * <p>{@code scheduling} — home to {@code AnchorPublisherJob}, anchor's own job runner — is
     * held to this same discipline, not left uncovered just because it's a separate package from
     * {@code anchor} itself: a release-readiness package-isolation audit found this gap (every
     * other job/adapter package had an explicit isolation test; this one, added the same phase as
     * {@code anchor} itself, had never been added to any), not merely assumed already covered.
     */
    @Test
    void anchorAndSchedulingPackagesNeverImportPersistenceOrMessagingPackagesAndViceVersa() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> {
            checkNoMutualImport(path, "/anchor/", ANCHOR_PACKAGE_SEGMENT, "/persistence/jpa/", JPA_PACKAGE_SEGMENT, violations);
            checkNoMutualImport(
                    path, "/anchor/", ANCHOR_PACKAGE_SEGMENT, "/persistence/mongo/", MONGO_PACKAGE_SEGMENT, violations);
            checkNoMutualImport(path, "/anchor/", ANCHOR_PACKAGE_SEGMENT, "/messaging/", MESSAGING_PACKAGE_SEGMENT, violations);
            checkNoMutualImport(
                    path, "/scheduling/", SCHEDULING_PACKAGE_SEGMENT, "/persistence/jpa/", JPA_PACKAGE_SEGMENT, violations);
            checkNoMutualImport(
                    path, "/scheduling/", SCHEDULING_PACKAGE_SEGMENT, "/persistence/mongo/", MONGO_PACKAGE_SEGMENT, violations);
            checkNoMutualImport(
                    path, "/scheduling/", SCHEDULING_PACKAGE_SEGMENT, "/messaging/", MESSAGING_PACKAGE_SEGMENT, violations);
        });
        assertThat(violations).isEmpty();
    }

    /**
     * {@code metrics} ({@code AuditMetricsRecorder} and its two implementations) must be a pure
     * leaf: every adapter/job package (persistence.jpa, persistence.mongo, messaging,
     * verification, anchor) depends on the plain {@code AuditMetricsRecorder} <em>interface</em>
     * this package lives in, but this package must never import back into any of theirs — unlike
     * the pairs above, this is deliberately one-directional, not mutual, since the whole point is
     * that those five packages may depend on {@code metrics}, just never the reverse.
     */
    @Test
    void metricsPackageNeverImportsAdapterOrJobPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> checkNoImportFrom(
                path,
                "/metrics/",
                List.of(
                        JPA_PACKAGE_SEGMENT,
                        MONGO_PACKAGE_SEGMENT,
                        MESSAGING_PACKAGE_SEGMENT,
                        VERIFICATION_PACKAGE_SEGMENT,
                        ANCHOR_PACKAGE_SEGMENT),
                violations));
        assertThat(violations).isEmpty();
    }

    /**
     * {@code sharding} ({@code PartitionShardResolver} and its default implementation) is the
     * same kind of pure leaf as {@code metrics}: {@code messaging} depends on the plain
     * {@code PartitionShardResolver} <em>interface</em> this package lives in, but this package
     * must never import back into any adapter/job package — one-directional, not mutual, same
     * reasoning as {@code metrics}.
     */
    @Test
    void shardingPackageNeverImportsAdapterOrJobPackages() throws IOException {
        List<String> violations = new ArrayList<>();
        forEachMainSourceFile(path -> checkNoImportFrom(
                path,
                "/sharding/",
                List.of(
                        JPA_PACKAGE_SEGMENT,
                        MONGO_PACKAGE_SEGMENT,
                        MESSAGING_PACKAGE_SEGMENT,
                        VERIFICATION_PACKAGE_SEGMENT,
                        ANCHOR_PACKAGE_SEGMENT),
                violations));
        assertThat(violations).isEmpty();
    }

    private void checkNoImportFrom(Path path, String pathMarker, List<String> forbiddenSegments, List<String> violations) {
        String normalizedPath = path.toString().replace('\\', '/');
        if (!normalizedPath.contains(pathMarker)) {
            return;
        }
        for (String line : readLines(path)) {
            String stripped = line.strip();
            if (stripped.startsWith("import ") && forbiddenSegments.stream().anyMatch(stripped::contains)) {
                violations.add(path + ": " + stripped);
            }
        }
    }

    private void forEachMainSourceFile(Consumer<Path> check) throws IOException {
        Path mainSourceRoot = Path.of(System.getProperty("user.dir")).resolve("src/main/java");
        try (Stream<Path> files = Files.walk(mainSourceRoot)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(check);
        }
    }

    private void checkForForbiddenImport(Path path, Pattern forbidden, List<String> violations) {
        for (String line : readLines(path)) {
            if (forbidden.matcher(line.strip()).matches()) {
                violations.add(path + ": " + line.strip());
            }
        }
    }

    private void checkNoMutualImport(
            Path path,
            String pathMarkerA,
            String forbiddenSegmentIfA,
            String pathMarkerB,
            String forbiddenSegmentIfB,
            List<String> violations) {
        String normalizedPath = path.toString().replace('\\', '/');
        boolean isA = normalizedPath.contains(pathMarkerA);
        boolean isB = normalizedPath.contains(pathMarkerB);
        if (!isA && !isB) {
            return;
        }
        String forbiddenSegment = isA ? forbiddenSegmentIfB : forbiddenSegmentIfA;
        for (String line : readLines(path)) {
            String stripped = line.strip();
            if (stripped.startsWith("import ") && stripped.contains(forbiddenSegment)) {
                violations.add(path + ": " + stripped);
            }
        }
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
