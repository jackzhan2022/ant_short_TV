package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.accounting.AiModelPointPriceComponentMapper;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiModelRoute;
import com.antshorttv.ai.AiModelRouter;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.points.AiPointLedgerMapper;
import com.antshorttv.points.AiPointPolicyComponentMapper;
import com.antshorttv.points.AiPointPolicyVersionMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.PointAccountingService;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.AssetVisualVariantService;
import com.antshorttv.script.EpisodeAwareVisualResolver;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class AiImageTaskServiceTest {

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING", "SETTLING", "RENDERING"})
    void cancelsInProgressTaskWithoutReopeningSettledAccounting(String phase) {
        Fixture fixture = new Fixture(phase);

        AiImageTaskResponse response = fixture.service.cancel(11L, 22L, 33L, fixture.request);

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(fixture.task.getStatus()).isEqualTo("CANCELED");
        verify(fixture.tasks).updateById(fixture.task);
        fixture.assertCanceledWithSettledAccounting();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING", "SETTLING", "RENDERING"})
    void regenerationSupersedesInProgressVariantWithoutReopeningSettledAccounting(String phase) {
        Fixture fixture = new Fixture(phase);
        AiModelEntity model = new AiModelEntity();
        model.setId(8L);
        model.setName("image-test");
        AiProviderEntity provider = new AiProviderEntity();
        provider.setCode("mock");
        when(fixture.router.route(8L, "IMAGE")).thenReturn(new AiModelRoute(model, provider, null, null));
        when(fixture.tasks.insert(any(AiImageTaskEntity.class))).thenAnswer(invocation -> {
            invocation.<AiImageTaskEntity>getArgument(0).setId(34L);
            return 1;
        });
        AiExecutionTaskEntity replacement = new AiExecutionTaskEntity();
        replacement.id = 200L;
        when(fixture.executions.regenerateWithReservation(
            eq(99L), eq(34L), eq(8L), anyString(), anyString(), any(), any()
        )).thenReturn(replacement);
        when(fixture.variants.replaceGenerationOwner(11L, 22L, 77L, 34L)).thenReturn(33L);

        AiImageTaskResponse response = fixture.service.regenerate(11L, 22L, 33L, fixture.request);

        assertThat(response.id()).isEqualTo(34L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(fixture.task.getStatus()).isEqualTo("CANCELED");
        verify(fixture.tasks).updateById(fixture.task);
        fixture.assertCanceledWithSettledAccounting();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SUCCESS", "FAILED", "CANCELED", "UNKNOWN"})
    void rejectsCancellationForTerminalAndUnknownStatuses(String status) {
        Fixture fixture = new Fixture(status);

        assertThatThrownBy(() -> fixture.service.cancel(11L, 22L, 33L, fixture.request))
            .isInstanceOf(BusinessException.class);

        verify(fixture.executions, never()).cancelWithDisposition(anyLong());
    }

    private static class Fixture {
        private final AiImageTaskMapper tasks = mock(AiImageTaskMapper.class);
        private final AiExecutionService executions = mock(AiExecutionService.class);
        private final AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
        private final AiPointLedgerMapper ledger = mock(AiPointLedgerMapper.class);
        private final PointAccountingService accounting = mock(PointAccountingService.class);
        private final AiModelRouter router = mock(AiModelRouter.class);
        private final AssetVisualVariantService variants = mock(AssetVisualVariantService.class);
        private final MockHttpServletRequest request = new MockHttpServletRequest();
        private final AiImageTaskEntity task = new AiImageTaskEntity();
        private final AiPointReservationEntity reservation = new AiPointReservationEntity();
        private final AiImageTaskService service;

        private Fixture(String phase) {
            TenantContextResolver tenants = mock(TenantContextResolver.class);
            ProjectMapper projects = mock(ProjectMapper.class);
            AiImageResultMapper results = mock(AiImageResultMapper.class);
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            AiPointSettlementService settlements = new AiPointSettlementService(
                jdbc, mock(AiPointPolicyVersionMapper.class), mock(AiPointPolicyComponentMapper.class),
                reservations, ledger, accounting, new ObjectMapper(), mock(AiModelPointPriceComponentMapper.class)
            );
            service = new AiImageTaskService(
                tenants, projects, mock(ProjectPermissionGuard.class), router, mock(ProjectAiConfigService.class),
                tasks, results, mock(MaterialMapper.class), executions, mock(AiExecutionResponseMapper.class),
                settlements, reservations, mock(AiImageStorageService.class), jdbc, mock(OperationLogService.class),
                variants, mock(EpisodeAwareVisualResolver.class)
            );
            task.setId(33L);
            task.setTenantId(11L);
            task.setProjectId(22L);
            task.setTargetType("VISUAL_VARIANT");
            task.setTargetId(77L);
            task.setTaskType("CHARACTER");
            task.setModelId(8L);
            task.setImageCount(1);
            task.setAspectRatio("1:1");
            task.setQuality("STANDARD");
            task.setPrompt("portrait");
            task.setExecutionId(99L);
            task.setStatus(phase);
            when(tenants.requireActiveMember(11L)).thenReturn(new TenantContext(66L, 11L, 88L, "OWNER"));
            when(projects.selectByTenantIdAndId(11L, 22L)).thenReturn(new ProjectEntity());
            when(tasks.selectOne(any())).thenReturn(task);
            when(tasks.selectById(33L)).thenReturn(task);
            when(results.selectActiveByTask(anyLong())).thenReturn(List.of());
            AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
            execution.id = 99L;
            execution.status = "RUNNING";
            execution.executionVersion = 1;
            when(executions.requireTask(99L)).thenReturn(execution);
            when(executions.cancelWithDisposition(99L)).thenAnswer(invocation -> {
                execution.status = "CANCELED";
                execution.claimToken = null;
                return new AiExecutionService.AiExecutionCancellation(execution, false);
            });
            reservation.id = 101L;
            reservation.executionId = 99L;
            reservation.status = "SETTLED";
            reservation.settledPoints = new BigDecimal("12.00000000");
            when(reservations.selectByExecutionId(99L)).thenReturn(reservation);
            when(reservations.selectById(101L)).thenReturn(reservation);
            request.addHeader("Idempotency-Key", "phase-regenerate");
        }

        private void assertCanceledWithSettledAccounting() {
            verify(executions).cancelWithDisposition(99L);
            verify(executions).updateSettlementSummary(reservation);
            assertThat(reservation.status).isEqualTo("SETTLED");
            assertThat(reservation.settledPoints).isEqualByComparingTo("12.00000000");
            verify(reservations, never()).updateById(any(AiPointReservationEntity.class));
            org.mockito.Mockito.verifyNoInteractions(accounting, ledger);
        }
    }
}
