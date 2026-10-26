package com.antshorttv.freecreation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.ai.*;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.security.*;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.video.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

class FreeCreationServiceTest {
    @Test void imageQuoteUsesLightweightReferencesWhileSubmissionStillInspectsMedia() throws Exception {
        var json=new ObjectMapper();var router=mock(AiModelRouter.class);var tenants=mock(TenantContextResolver.class);
        var context=new TenantContext(2L,1L,3L,"OWNER");
        when(tenants.requireActiveMember(1L)).thenReturn(context);
        var model=new AiModelEntity();model.setId(4L);model.setModelCode("gpt-image-2");model.setConfigJson("{}");
        var provider=new AiProviderEntity();provider.setCode("OpenAI");
        when(router.route(4L,"IMAGE")).thenReturn(new AiModelRoute(model,provider,new AiProviderConfigEntity(),mock(AiProviderAdapter.class)));
        var media=mock(FreeCreationMediaService.class);
        var ref=new FreeCreationRequest.Reference("IMAGE","completed-upload",null);
        var image=new FreeCreationMediaEntity();image.kind="IMAGE";
        when(media.referenceForImageEstimate(context,ref)).thenReturn(image);
        when(media.reference(context,ref)).thenReturn(image);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var service=new FreeCreationService(mock(FreeCreationMapper.class),mock(FreeCreationMediaMapper.class),media,tenants,
                router,mock(AiExecutionService.class),mock(AiVideoTaskService.class),mock(SeedanceVideoRequestValidator.class),
                mock(ObjectStorageService.class),json,mock(JdbcTemplate.class),factory.getValidator());
            var request=new FreeCreationRequest(null,"IMAGE",4L,"绘制山水",java.util.Map.of(),java.util.List.of(ref));
            var estimate=service.pointEstimate(1L,json.valueToTree(request));
            verify(media).referenceForImageEstimate(context,ref);
            verify(media,never()).reference(any(),any());
            var submission=service.prepare(context,request);
            verify(media).reference(context,ref);
            assertThat(estimate.usage()).isEqualTo(submission.usage());
            assertThat(estimate.dimensions()).isEqualTo(submission.dimensions());
        }
    }
    @ParameterizedTest
    @CsvSource({
        "1,10,true,true,FAILED",
        "2,10,true,false,FAILED",
        "1,11,true,false,FAILED",
        "1,10,false,false,FAILED",
        "1,10,true,false,SUCCEEDED"
    })
    void historicalErrorsUseOnlyRecognizedLogsFromTheSameTenantAndExecution(
        long logTenant,long logExecution,boolean knownError,boolean expectedPrivacy,String status) {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:free-error-"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("create table app_user (id bigint primary key, nickname varchar(100))");
        jdbc.execute("create table ai_call_log (id bigint primary key, tenant_id bigint, execution_id bigint, error_message varchar(1000))");
        jdbc.update("insert into app_user values (2,'创作者')");
        String raw=knownError?"VALIDATION_ERROR InputImageSensitiveContentDetected.PrivacyInformation: may contain real person. Request id: private":"Unknown error https://private.example Authorization: secret";
        jdbc.update("insert into ai_call_log values (7,?,?,?)",logTenant,logExecution,raw);
        var executions=mock(AiExecutionService.class);
        var task=new com.antshorttv.execution.AiExecutionTaskEntity();task.id=10L;task.status=status;
        task.errorMessage="生成服务暂时不可用，请稍后重试。";
        when(executions.requireTask(10L)).thenReturn(task);
        var item=new FreeCreationEntity();item.id=3L;item.tenantId=1L;item.creatorId=2L;item.executionId=10L;item.billingCallLogId=7L;
        var service=new FreeCreationService(mock(FreeCreationMapper.class),mock(FreeCreationMediaMapper.class),mock(FreeCreationMediaService.class),
            mock(TenantContextResolver.class),mock(AiModelRouter.class),executions,
            mock(AiVideoTaskService.class),mock(SeedanceVideoRequestValidator.class),mock(ObjectStorageService.class),
            new ObjectMapper(),jdbc,mock(jakarta.validation.Validator.class));
        var result=service.present(new TenantContext(2L,1L,3L,"OWNER"),item);
        if("SUCCEEDED".equals(status)) assertThat(result.get("errorMessage")).isNull();
        else assertThat(result.get("errorMessage")).isEqualTo(expectedPrivacy
            ?"参考图片未通过模型服务商审核，可能涉及真人形象，请更换符合该模型要求的参考图片后重试。"
            :"生成服务暂时不可用，请稍后重试。");
        jdbc.execute("shutdown");
    }

    @Test void pricingUsesTheSelectedModelsQualityAndRejectsUnsupportedHigherQuality() throws Exception {
        var json=new ObjectMapper();var router=mock(AiModelRouter.class);var tenants=mock(TenantContextResolver.class);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(2L,1L,3L,"OWNER"));
        var model=new AiModelEntity();model.setId(4L);model.setModelCode("gpt-image-2.5-sunburst");model.setConfigJson("{\"quality\":\"max\",\"outputFormat\":\"webp\"}");
        var provider=new AiProviderEntity();provider.setCode("OpenAI");
        when(router.route(4L,"IMAGE")).thenReturn(new AiModelRoute(model,provider,new AiProviderConfigEntity(),mock(AiProviderAdapter.class)));
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var service=new FreeCreationService(mock(FreeCreationMapper.class),mock(FreeCreationMediaMapper.class),mock(FreeCreationMediaService.class),tenants,
                router,mock(AiExecutionService.class),mock(AiVideoTaskService.class),mock(SeedanceVideoRequestValidator.class),
                mock(ObjectStorageService.class),json,mock(JdbcTemplate.class),factory.getValidator());
            var request=json.readTree("{\"mediaType\":\"IMAGE\",\"modelId\":4,\"prompt\":\"绘制山水\"}");
            var plan=service.pointEstimate(1L,request);
            assertThat(plan.dimensions()).containsEntry("quality","max");
            assertThat(plan.usage()).containsEntry(AiUsageMetric.CALL,BigDecimal.ONE).containsEntry(AiUsageMetric.IMAGE,BigDecimal.ONE);
            model.setModelCode("gpt-image-2");
            assertThatThrownBy(()->service.pointEstimate(1L,request)).isInstanceOf(BusinessException.class).hasMessageContaining("不支持此图片质量");
        }
    }
}
