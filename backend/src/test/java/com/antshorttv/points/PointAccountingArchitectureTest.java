package com.antshorttv.points;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointAccountingArchitectureTest {

    private static final Path PRODUCTION_ROOT = Path.of("src/main/java");

    @Test
    void productionCodeUsesOnlyUnifiedPointMutationBoundary() throws IOException {
        List<Path> violations;
        try (var files = Files.walk(PRODUCTION_ROOT)) {
            violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("PointAccountingService.java"))
                .filter(this::containsLegacyPointMutation)
                .toList();
        }

        assertThat(violations)
            .as("legacy point mutation references outside PointAccountingService")
            .isEmpty();
    }

    @Test
    void onlyCommercialServicesCanGrantPoints() throws IOException {
        List<Path> violations;
        try (var files = Files.walk(PRODUCTION_ROOT)) {
            violations = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("PointAccountingService.java"))
                .filter(path -> !path.toString().contains("com\\antshorttv\\commercial"))
                .filter(this::callsPointGrant)
                .toList();
        }

        assertThat(violations)
            .as("point grants outside commercial package fulfillment")
            .isEmpty();
    }

    private boolean containsLegacyPointMutation(Path path) {
        try {
            String source = Files.readString(path);
            return source.contains("consumeForAi(")
                || source.contains("team_point_transaction")
                || source.contains("ai_point_ledger")
                || (source.contains("update team_point_account")
                    && !path.getFileName().toString().equals("PointAccountingService.java"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }

    private boolean callsPointGrant(Path path) {
        try {
            return Files.readString(path).contains(".grant(");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect " + path, exception);
        }
    }
}
