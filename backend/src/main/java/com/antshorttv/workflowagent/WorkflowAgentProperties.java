package com.antshorttv.workflowagent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "ai.workflow-agent")
public class WorkflowAgentProperties {
    @NotNull
    private Path skillRoot = Path.of("skills");

    @Min(1024)
    private long maxSkillFileBytes = 1_048_576L;

    @Min(1)
    @Max(100)
    private int maxSteps = 20;

    @Min(1)
    private long runTimeoutSeconds = 300L;

    @Min(1024)
    private long maxLogPayloadBytes = 262_144L;

    public Path getSkillRoot() {
        return skillRoot;
    }

    public void setSkillRoot(Path skillRoot) {
        this.skillRoot = skillRoot;
    }

    public long getMaxSkillFileBytes() {
        return maxSkillFileBytes;
    }

    public void setMaxSkillFileBytes(long maxSkillFileBytes) {
        this.maxSkillFileBytes = maxSkillFileBytes;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public long getRunTimeoutSeconds() {
        return runTimeoutSeconds;
    }

    public void setRunTimeoutSeconds(long runTimeoutSeconds) {
        this.runTimeoutSeconds = runTimeoutSeconds;
    }

    public long getMaxLogPayloadBytes() {
        return maxLogPayloadBytes;
    }

    public void setMaxLogPayloadBytes(long maxLogPayloadBytes) {
        this.maxLogPayloadBytes = maxLogPayloadBytes;
    }
}
