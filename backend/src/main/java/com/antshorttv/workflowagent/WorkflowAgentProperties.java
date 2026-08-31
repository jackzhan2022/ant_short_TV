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

    @Min(1)
    @Max(16)
    private int splitChunkConcurrency = 3;

    @Min(1000)
    private int splitChunkTargetMin = 15_000;

    @Min(1000)
    private int splitChunkTargetMax = 20_000;

    @Min(1000)
    private int splitChunkHardMax = 24_000;

    @Min(0)
    private int splitChunkOverlap = 1_500;

    @Min(1)
    private int splitSafeContextTokens = 800_000;

    @Min(0)
    private int splitPromptReserveTokens = 12_000;

    @Min(0)
    private int splitToolReserveTokens = 24_000;

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

    public int getSplitChunkConcurrency() { return splitChunkConcurrency; }
    public void setSplitChunkConcurrency(int value) { this.splitChunkConcurrency = value; }
    public int getSplitChunkTargetMin() { return splitChunkTargetMin; }
    public void setSplitChunkTargetMin(int value) { this.splitChunkTargetMin = value; }
    public int getSplitChunkTargetMax() { return splitChunkTargetMax; }
    public void setSplitChunkTargetMax(int value) { this.splitChunkTargetMax = value; }
    public int getSplitChunkHardMax() { return splitChunkHardMax; }
    public void setSplitChunkHardMax(int value) { this.splitChunkHardMax = value; }
    public int getSplitChunkOverlap() { return splitChunkOverlap; }
    public void setSplitChunkOverlap(int value) { this.splitChunkOverlap = value; }
    public int getSplitSafeContextTokens() { return splitSafeContextTokens; }
    public void setSplitSafeContextTokens(int value) { this.splitSafeContextTokens = value; }
    public int getSplitPromptReserveTokens() { return splitPromptReserveTokens; }
    public void setSplitPromptReserveTokens(int value) { this.splitPromptReserveTokens = value; }
    public int getSplitToolReserveTokens() { return splitToolReserveTokens; }
    public void setSplitToolReserveTokens(int value) { this.splitToolReserveTokens = value; }
}
