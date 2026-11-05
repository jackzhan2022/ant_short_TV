package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.monitoring.MonitoringProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationsSettingsServiceTest {
    @Test void defaultsObserveAndNeverExposeChannelSecrets() {
        var settings = settings();
        assertThat(settings.telemetryEnabled()).isTrue();
        assertThat(settings.notificationsEnabled(Instant.now())).isFalse();
        assertThat(settings.current().toString()).doesNotContain("secret-channel", "signing-secret");
        assertThat(settings.current()).containsEntry("minuteRetentionDays",7).containsEntry("channelConfigured",true);
    }
    @Test void rejectsUnknownKeysInvalidRetentionAndConcurrentWrites() {
        var service = settings();
        assertThatThrownBy(() -> service.update(Map.of("version",0,"feishuSecret","oops"))).isInstanceOf(com.antshorttv.common.BusinessException.class);
        assertThatThrownBy(() -> service.update(Map.of("version",0,"hourlyRetentionDays",1))).isInstanceOf(com.antshorttv.common.BusinessException.class);
        var saved = service.update(Map.of("version",0,"refreshSeconds",30));
        assertThat(saved).containsEntry("version",1L);
        assertThatThrownBy(() -> service.update(Map.of("version",0,"refreshSeconds",60))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.current()).containsEntry("refreshSeconds",30);
    }
    private OperationsSettingsService settings() {
        return new OperationsSettingsService(new OperationsDatabase(OperationsTestDatabase.create()),new ObjectMapper(),
            new MonitoringProperties(false,"secret-channel","signing-secret","test","test-one",10,true));
    }
}
