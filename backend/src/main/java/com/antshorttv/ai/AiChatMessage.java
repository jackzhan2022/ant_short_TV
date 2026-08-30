package com.antshorttv.ai;

import java.util.List;

public record AiChatMessage(
    AiChatRole role,
    String content,
    String toolCallId,
    List<AiToolCall> toolCalls
) {
    public AiChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage(AiChatRole.SYSTEM, content, null, List.of());
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage(AiChatRole.USER, content, null, List.of());
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage(AiChatRole.ASSISTANT, content, null, List.of());
    }

    public static AiChatMessage assistantToolCalls(List<AiToolCall> toolCalls) {
        return new AiChatMessage(AiChatRole.ASSISTANT, null, null, toolCalls);
    }

    public static AiChatMessage toolResult(String toolCallId, String content) {
        return new AiChatMessage(AiChatRole.TOOL, content, toolCallId, List.of());
    }
}
