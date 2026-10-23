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
