package com.antshorttv.workflowagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WorkflowSkillStorageHealth implements ApplicationRunner {
    private final WorkflowAgentProperties properties;
    private final Environment environment;

    public WorkflowSkillStorageHealth(
        WorkflowAgentProperties properties,
        Environment environment
    ) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                || "production".equalsIgnoreCase(profile));
        validateStorage(properties.getSkillRoot(), production);
    }

    static Path validateStorage(Path configuredRoot, boolean production) {
        if (configuredRoot == null) {
            throw new IllegalStateException("Workflow Skill root is required.");
        }
        if (production && !configuredRoot.isAbsolute()) {
            throw new IllegalStateException(
                "AI_WORKFLOW_SKILL_ROOT must be an absolute persistent path in production."
            );
        }
        Path root = configuredRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                throw new IllegalStateException("Workflow Skill root is not a writable directory.");
            }
            Path source = Files.createTempFile(root, ".workflow-skill-health-", ".tmp");
            Path target = root.resolve(source.getFileName() + ".moved");
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(source);
                Files.deleteIfExists(target);
            }
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Workflow Skill root must support writable atomic file replacement.", exception
            );
        }
    }
}
