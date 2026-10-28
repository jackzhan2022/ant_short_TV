package com.antshorttv.downloadcenter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DownloadCenterServiceTest {
    JdbcTemplate jdbc;
    TenantContextResolver tenants;
    DownloadSourceResolver sources;
    ObjectStorageService storage;
    DownloadArchiveService archives;
    DownloadCenterService service;
    MutableClock clock;
    final DownloadTask.Request request = new DownloadTask.Request("测试下载", List.of(new DownloadTask.Source("IMAGE", 8L, 9L, null)));

    @BeforeEach void setup() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:downloads" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V169__download_center.sql").readAllBytes(), StandardCharsets.UTF_8));
        jdbc.execute(new String(getClass().getResourceAsStream("/db/migration/V170__download_delivery_metadata.sql").readAllBytes(), StandardCharsets.UTF_8));
        tenants = mock(TenantContextResolver.class); sources = mock(DownloadSourceResolver.class); storage = mock(ObjectStorageService.class);
        archives = mock(DownloadArchiveService.class);
        clock = new MutableClock(Instant.parse("2026-10-28T00:00:00Z"));
        service = new DownloadCenterService(jdbc, tenants, sources, archives, storage, new ObjectMapper().findAndRegisterModules(), clock);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(2L,1L,3L,"OWNER"));
        when(sources.normalize(1L, request.sources())).thenReturn(request.sources());
        when(sources.resolve(1L, request.sources())).thenReturn(List.of(new DownloadSourceResolver.File("图片.png", () -> new ByteArrayInputStream(new byte[]{1,2,3}))));
        when(archives.generate(anyString(),anyList(),any())).thenReturn(100L);
        when(storage.downloadUrl(anyString(),anyString(),any())).thenReturn("https://storage.example/signed");
        when(storage.metadata(anyString())).thenAnswer(call->new com.antshorttv.storage.StoredObject(call.getArgument(0),100L,"image/png","etag","STANDARD"));
    }
    @Test void expiryStartsAtSuccessfulCompletionAndDeletesActualArchive() {
        var task=service.create(1L,request); assertThat(task.status()).isEqualTo("QUEUED"); assertThat(task.expiresAt()).isNull();
        clock.advance(Duration.ofHours(2)); assertThat(service.claim(task.id())).isTrue(); service.generate(task.id());
        var ready=service.detail(1L,task.id());
        assertThat(ready.completedAt()).isEqualTo(clock.instant()); assertThat(ready.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        service.download(1L,task.id()); service.download(1L,task.id());
        clock.advance(Duration.ofHours(24));
        assertThatThrownBy(()->service.download(1L,task.id())).isInstanceOf(BusinessException.class);
        assertThat(service.detail(1L,task.id()).status()).isEqualTo("EXPIRED");
        service.cleanup(); verify(storage).delete("download-archives/1/2/"+task.id()+".file");
        service.cleanup(); verify(storage,times(1)).delete(anyString());
        assertThat(service.detail(1L,task.id()).status()).isEqualTo("EXPIRED");
    }
    @Test void failedPhysicalDeletionIsRetriedWhileDownloadsRemainBlocked() {
        var task=service.create(1L,request);service.claim(task.id());service.generate(task.id());clock.advance(Duration.ofHours(24));
        doThrow(new IllegalStateException("storage unavailable")).doNothing().when(storage).delete(anyString());
        service.cleanup();assertThatThrownBy(()->service.download(1L,task.id())).isInstanceOf(BusinessException.class);
        service.cleanup();verify(storage,times(2)).delete(anyString());
    }
    @Test void ownerAndTenantAreEnforced() {
        var task=service.create(1L,request);
        when(tenants.requireActiveMember(1L)).thenReturn(new TenantContext(99L,1L,4L,"OWNER"));
        assertThat(service.list(1L,null,null,1,10).total()).isZero();
        assertThatThrownBy(()->service.detail(1L,task.id())).isInstanceOf(BusinessException.class);
        when(tenants.requireActiveMember(3L)).thenReturn(new TenantContext(2L,3L,5L,"OWNER"));
        assertThatThrownBy(()->service.detail(3L,task.id())).isInstanceOf(BusinessException.class);
    }
    @Test void failureHasNoExpiryAndRetryCreatesNewTask() {
        var task=service.create(1L,request);service.claim(task.id());
        when(archives.generate(anyString(),anyList(),any())).thenThrow(new IllegalStateException("remote failed"));service.generate(task.id());
        var failed=service.detail(1L,task.id());assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.expiresAt()).isNull();
        assertThat(service.retry(1L,task.id()).id()).isNotEqualTo(task.id());
        assertThatThrownBy(()->service.download(1L,task.id())).isInstanceOf(BusinessException.class);
    }
    @Test void onlyOneWorkerCanClaimAndInterruptedTasksAreRecovered() {
        var task=service.create(1L,request); assertThat(service.claim(task.id())).isTrue();assertThat(service.claim(task.id())).isFalse();
        clock.advance(Duration.ofMinutes(6));service.cleanup();assertThat(service.detail(1L,task.id()).status()).isEqualTo("FAILED");
        service.generate(task.id());assertThat(service.detail(1L,task.id()).status()).isEqualTo("FAILED");
    }
    @Test void legacyZipTasksKeepTheirExistingObjectPathAndFilename() {
        var task=service.create(1L,request);
        jdbc.update("update download_task set output_format=null,file_name=null,content_type=null where id=?",task.id());
        service.claim(task.id());service.generate(task.id());
        assertThat(service.detail(1L,task.id()).outputFormat()).isEqualTo("ZIP");
        assertThat(service.detail(1L,task.id()).fileName()).isEqualTo("测试下载.zip");
        service.download(1L,task.id());
        verify(storage).downloadUrl(eq("download-archives/1/2/"+task.id()+".zip"),eq("测试下载.zip"),any());
    }
    @Test void signedDeliveryIsBoundedByExpiryAndCachedFilesStillRequireAuthorization() {
        var task=service.create(1L,request);service.claim(task.id());service.generate(task.id());
        assertThat(service.detail(1L,task.id()).fileName()).isEqualTo("图片.png");
        var delivery=service.delivery(1L,task.id());
        assertThat(delivery.outputFormat()).isEqualTo("FILE");assertThat(delivery.cacheKey()).startsWith("/__download-cache/1/2/");
        verify(storage).downloadUrl(anyString(),eq("图片.png"),eq(clock.instant().plusSeconds(60)));
        clock.advance(Duration.ofHours(24).minusSeconds(10));service.delivery(1L,task.id());
        verify(storage).downloadUrl(anyString(),eq("图片.png"),eq(clock.instant().plusSeconds(10)));
        when(sources.resolve(1L,request.sources())).thenThrow(new BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN,"无权访问"));
        assertThatThrownBy(()->service.delivery(1L,task.id())).isInstanceOf(BusinessException.class);
    }
    @Test void multipleResolvedFilesUseReadableTimestampedZipNames() {
        var inputs=List.of(new DownloadTask.Source("IMAGE",8L,9L,null),new DownloadTask.Source("IMAGE",8L,10L,null));
        when(sources.normalize(1L,inputs)).thenReturn(inputs);
        when(sources.resolve(1L,inputs)).thenReturn(List.of(new DownloadSourceResolver.File("人物.png",()->null),new DownloadSourceResolver.File("场景.png",()->null)));
        when(sources.bundleName(1L,inputs,"资产图片")).thenReturn("星河_资产图片");
        var task=service.create(1L,new DownloadTask.Request("资产图片",inputs));
        assertThat(task.outputFormat()).isEqualTo("ZIP");assertThat(task.fileCount()).isEqualTo(2);
        assertThat(task.fileName()).startsWith("星河_资产图片_20261028_080000_").endsWith(".zip");
        service.claim(task.id());service.generate(task.id());
        verify(archives).generate(eq("download-archives/1/2/"+task.id()+".zip"),anyList(),any());
    }
    static class MutableClock extends Clock {
        Instant value;MutableClock(Instant value){this.value=value;}void advance(Duration delta){value=value.plus(delta);}
        public Instant instant(){return value;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
    }
}
