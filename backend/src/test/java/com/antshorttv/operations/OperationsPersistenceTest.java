package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OperationsPersistenceTest {
    @Test void createsOnlyPlatformGrantsAndRejectsDuplicateEventKeys() {
        var jdbc = OperationsTestDatabase.create();
        assertThat(jdbc.queryForObject("select count(*) from platform_role_permission where role_id=1",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from platform_role_permission where role_id=2",Integer.class)).isZero();
        jdbc.update("insert into ops_event(event_key,occurred_at,source) values('one',current_timestamp,'API')");
        assertThatThrownBy(() -> jdbc.update("insert into ops_event(event_key,occurred_at,source) values('one',current_timestamp,'API')")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("select count(*) from ops_alert_event",Integer.class)).isZero();
    }
}
