package com.antshorttv.operationlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OperationLogServiceTest {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Test
    void recordsOperationLog() {
        operationLogService.record(1L, 2L, "UNIT_TEST_LOGIN", 1L, OperationResult.SUCCESS, null);

        OperationLogEntity log = operationLogMapper.selectOne(
            new LambdaQueryWrapper<OperationLogEntity>().eq(OperationLogEntity::getOperation, "UNIT_TEST_LOGIN"));

        assertThat(log.getUserId()).isEqualTo(1L);
        assertThat(log.getTenantId()).isEqualTo(2L);
        assertThat(log.getResult()).isEqualTo(OperationResult.SUCCESS.name());
    }

    @Test
    void recordsStructuredOperationDetail() {
        operationLogService.record(
            3L,
            4L,
            "PLATFORM_UPDATE_TENANT_STATUS",
            4L,
            OperationResult.SUCCESS,
            null,
            Map.of("previousStatus", "ACTIVE", "newStatus", "DISABLED", "source", "PLATFORM")
        );

        OperationLogEntity log = operationLogMapper.selectOne(
            new LambdaQueryWrapper<OperationLogEntity>()
                .eq(OperationLogEntity::getOperation, "PLATFORM_UPDATE_TENANT_STATUS"));

        assertThat(log.getDetailJson()).contains("\"previousStatus\":\"ACTIVE\"");
        assertThat(log.getDetailJson()).contains("\"newStatus\":\"DISABLED\"");
        assertThat(log.getDetailJson()).contains("\"source\":\"PLATFORM\"");
    }
}
