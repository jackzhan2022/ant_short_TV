package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.ai.*;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.*;
import com.antshorttv.points.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.video.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class FreeCreationExecutionHandlerTest {
    final FreeCreationMapper creations=mock(FreeCreationMapper.class);
    final FreeCreationService service=mock(FreeCreationService.class);
    final AiInvocationService invocation=mock(AiInvocationService.class);
    final AiExecutionService executions=mock(AiExecutionService.class);
    final AiPointReservationMapper reservations=mock(AiPointReservationMapper.class);
    final AiPointSettlementService settlements=mock(AiPointSettlementService.class);
    final FreeCreationExecutionHandler handler=new FreeCreationExecutionHandler(creations,service,mock(FreeCreationMediaService.class),
        invocation,mock(AiVideoProviderAdapter.class),mock(SeedanceArkVideoProviderAdapter.class),mock(AiVideoTaskService.class),
        mock(ObjectStorageService.class),executions,mock(AiExecutionTaskMapper.class),mock(AiExecutionClaimService.class),
        reservations,settlements,mock(AiUsageAccountingService.class),new ObjectMapper(),mock(JdbcTemplate.class),mock(PlatformTransactionManager.class));

    @Test void pollAuthenticationFailureKeepsReservationForReviewWithMatchingAttemptAndCallLog() {
        var context=context();var creation=creation();
        when(creations.selectById(2L)).thenReturn(creation);when(creations.update(any(),any())).thenReturn(1);
        when(executions.requireTaskForUpdate(1L)).thenReturn(context.task());
        when(service.videoTask(creation,true)).thenReturn(new AiVideoTaskEntity());
        when(service.read(null)).thenReturn(new ObjectMapper().createObjectNode());
        when(invocation.invokeProviderNative(any(),anyString(),any())).thenThrow(new AiGatewayException(ErrorCode.AI_AUTH_FAILED,"Authorization: private",7L));
        var reserved=new AiPointReservationEntity();reserved.id=8L;reserved.status="RESERVED";
        when(reservations.selectByExecutionId(1L)).thenReturn(reserved);
        when(settlements.finalizeOutcome(anyLong(),any(),anyMap(),any(),any(),anyString())).thenReturn(reserved);
        assertThatThrownBy(()->handler.execute(context)).isInstanceOf(IllegalStateException.class).hasMessage("生成服务暂时不可用，请稍后重试。");
        verify(settlements).finalizeOutcome(eq(8L),eq(AiSettlementOutcome.TRANSPORT_UNKNOWN),eq(Map.of()),eq(5L),eq(7L),anyString());
        assertThat(creation.billingAttemptId).isEqualTo(5L);assertThat(creation.billingCallLogId).isEqualTo(7L);
    }

    @Test void imagePrivacyRejectionExplainsReferenceIssueAndStillReleasesReservation() {
        var context=context();var creation=creation();
        creation.externalTaskId=null;creation.providerStartedAt=null;
        when(creations.selectById(2L)).thenReturn(creation);when(creations.update(any(),any())).thenReturn(1);
        when(executions.requireTaskForUpdate(1L)).thenReturn(context.task());
        when(service.videoTask(creation,true)).thenReturn(new AiVideoTaskEntity());
        when(service.read(null)).thenReturn(new ObjectMapper().createObjectNode());
        when(invocation.invokeProviderNative(any(),anyString(),any())).thenThrow(new AiGatewayException(ErrorCode.VALIDATION_ERROR,
            "火山方舟请求失败，HTTP 400：InputImageSensitiveContentDetected.PrivacyInformation: The input image may contain real person. Request id: private-request",7L));
        var reserved=new AiPointReservationEntity();reserved.id=8L;reserved.status="RESERVED";
        when(reservations.selectByExecutionId(1L)).thenReturn(reserved);
        when(settlements.finalizeOutcome(anyLong(),any(),anyMap(),any(),any(),anyString())).thenReturn(reserved);
        assertThatThrownBy(()->handler.execute(context)).isInstanceOf(IllegalStateException.class)
            .hasMessage("参考图片未通过模型服务商审核，可能涉及真人形象，请更换符合该模型要求的参考图片后重试。");
        verify(settlements).finalizeOutcome(eq(8L),eq(AiSettlementOutcome.PROVIDER_REJECTION),eq(Map.of()),eq(5L),eq(7L),anyString());
        assertThat(creation.billingCallLogId).isEqualTo(7L);
    }

    @Test void staleWorkerCannotSettleAfterItsClaimWasReplaced() {
        var context=context();var creation=creation();
        when(creations.selectById(2L)).thenReturn(creation);when(creations.update(any(),any())).thenReturn(1);
        when(service.videoTask(creation,true)).thenReturn(new AiVideoTaskEntity());
        when(invocation.invokeProviderNative(any(),anyString(),any())).thenThrow(new AiGatewayException(ErrorCode.AI_AUTH_FAILED,"expired",7L));
        var replacement=context();replacement.task().claimToken="new-worker";
        when(executions.requireTaskForUpdate(1L)).thenReturn(replacement.task());
        assertThatThrownBy(()->handler.execute(context)).isInstanceOf(AiExecutionClaimLostException.class);
        verifyNoInteractions(settlements,reservations);
    }

    @Test void expiredQueuedCreationReleasesReservationWithoutCallingProvider() {
        var context=context();var creation=creation();
        creation.externalTaskId=null;creation.providerStartedAt=null;creation.createdAt=LocalDateTime.now().minusHours(1);
        creation.billingAttemptId=null;creation.billingCallLogId=null;
        when(creations.selectById(2L)).thenReturn(creation);
        when(executions.requireTaskForUpdate(1L)).thenReturn(context.task());
        var reserved=new AiPointReservationEntity();reserved.id=8L;reserved.status="RESERVED";
        when(reservations.selectByExecutionId(1L)).thenReturn(reserved);
        when(settlements.finalizeOutcome(anyLong(),any(),anyMap(),any(),any(),anyString())).thenAnswer(call->{reserved.status="RELEASED";return reserved;});
        assertThatThrownBy(()->handler.execute(context)).isInstanceOf(IllegalStateException.class).hasMessageContaining("生成超时");
        verify(settlements).finalizeOutcome(eq(8L),eq(AiSettlementOutcome.PRE_CALL_CANCELED),eq(Map.of()),any(),any(),anyString());
        verifyNoInteractions(invocation);
    }
    private AiExecutionContext context() {
        var task=new AiExecutionTaskEntity();task.id=1L;task.tenantId=3L;task.businessId=2L;task.status="RUNNING";
        task.claimToken="worker";task.executionVersion=1;task.scene="free_creation_video";
        return new AiExecutionContext(task,new AiExecutionClaim(1L,5L,"worker",1,"QUERY"));
    }
    private FreeCreationEntity creation() {
        var creation=new FreeCreationEntity();creation.id=2L;creation.executionId=1L;creation.tenantId=3L;creation.creatorId=4L;
        creation.mediaType="VIDEO";creation.modelId=6L;creation.providerCode="VOLCENGINE_ARK";creation.prompt="海面";
        creation.externalTaskId="accepted-task";creation.createdAt=LocalDateTime.now();creation.providerStartedAt=creation.createdAt;
        creation.billingAttemptId=2L;creation.billingCallLogId=3L;return creation;
    }
}
