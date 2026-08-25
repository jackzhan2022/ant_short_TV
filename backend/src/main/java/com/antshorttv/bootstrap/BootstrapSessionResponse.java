package com.antshorttv.bootstrap;

import java.time.LocalDateTime;

public record BootstrapSessionResponse(String sessionId, LocalDateTime expiresAt) {
}
