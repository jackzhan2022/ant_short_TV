package com.antshorttv.downloadcenter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.storage.ObjectStorageService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class DownloadArchiveServiceTest {
    @Test void storedSingleFileIsCopiedInsideCosWithoutReadingBytesOnServer() {
        var storage=mock(ObjectStorageService.class);var service=new DownloadArchiveService(storage);
        String key="download-archives/1/2/"+UUID.randomUUID()+".file";
        when(storage.copyForDownload("materials/1/2/original.mp4",key)).thenReturn(new com.antshorttv.storage.StoredObject(key,123L,"video/mp4","etag","STANDARD"));
        var file=new DownloadSourceResolver.File("星河_第01集.mp4","materials/1/2/original.mp4","video/mp4",()->{throw new AssertionError("Application must not read original bytes");});
        assertThat(service.generate(key,List.of(file),()->{})).isEqualTo(123L);
        verify(storage).copyForDownload("materials/1/2/original.mp4",key);verifyNoMoreInteractions(storage);
    }
    @Test void singleFileKeepsOriginalBytesInsteadOfZip() throws Exception {
        var storage=mock(ObjectStorageService.class);var service=new DownloadArchiveService(storage);
        AtomicReference<byte[]> uploaded=new AtomicReference<>();
        doAnswer(call->{uploaded.set(Files.readAllBytes(call.getArgument(1)));return null;})
            .when(storage).uploadFile(anyString(),any(Path.class),anyString());
        byte[] original="审核报告正文".getBytes(StandardCharsets.UTF_8);
        long size=service.generate("download-archives/1/2/"+UUID.randomUUID()+".file",
            List.of(new DownloadSourceResolver.File("审核报告.md",()->new ByteArrayInputStream(original))),()->{});
        assertThat(uploaded.get()).isEqualTo(original);assertThat(size).isEqualTo(original.length);
    }
    @Test void longUnicodeNamesKeepExtensionAndStayWithinFilesystemByteLimit() {
        String name=DownloadArchiveService.uniqueName("项目".repeat(120)+".mp4",new HashSet<>());
        assertThat(name).endsWith(".mp4");assertThat(name.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(240);
        assertThat(DownloadArchiveService.uniqueName("CON.txt",new HashSet<>())).isNotEqualTo("CON.txt");
    }
    @Test void buildsOneValidZipWithSafeUniqueNamesAndRemovesTemporaryFile() throws Exception {
        var storage=mock(ObjectStorageService.class);var service=new DownloadArchiveService(storage);
        AtomicReference<Path> path=new AtomicReference<>();Map<String,String> entries=new LinkedHashMap<>();
        doAnswer(call->{
            path.set(call.getArgument(1));
            try(var zip=new ZipInputStream(Files.newInputStream(path.get()),StandardCharsets.UTF_8)) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null)entries.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
            }return null;
        }).when(storage).uploadFile(anyString(),any(Path.class),eq("application/zip"));
        String id=UUID.randomUUID().toString();
        long size=service.generate("download-archives/1/2/"+id+".zip",List.of(
            new DownloadSourceResolver.File("图片.png",()->new ByteArrayInputStream("one".getBytes())),
            new DownloadSourceResolver.File("图片.png",()->new ByteArrayInputStream("two".getBytes())),
            new DownloadSourceResolver.File("../unsafe.txt",()->new ByteArrayInputStream("three".getBytes()))),()->{});
        assertThat(size).isPositive();assertThat(entries).containsEntry("图片.png","one").containsEntry("图片 (2).png","two");
        assertThat(entries.keySet()).noneMatch(name->name.contains("/")||name.contains("\\"));
        assertThat(Files.exists(path.get())).isFalse();
    }
    @Test void neverPublishesPartialArchiveWhenAnySourceFails() {
        var storage=mock(ObjectStorageService.class);var service=new DownloadArchiveService(storage);String id=UUID.randomUUID().toString();
        assertThatThrownBy(()->service.generate("download-archives/1/2/"+id+".zip",List.of(
            new DownloadSourceResolver.File("good.txt",()->new ByteArrayInputStream(new byte[]{1})),
            new DownloadSourceResolver.File("missing.txt",()->{throw new IOException("missing");})),()->{})).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
        assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"),"antv-download-center",id+".zip.part"))).isFalse();
    }
    @Test void cleansLocalFileWhenUploadFails() throws Exception {
        var storage=mock(ObjectStorageService.class);var service=new DownloadArchiveService(storage);String id=UUID.randomUUID().toString();
        doThrow(new IllegalStateException("upload failed")).when(storage).uploadFile(anyString(),any(Path.class),anyString());
        assertThatThrownBy(()->service.generate("download-archives/1/2/"+id+".zip",List.of(new DownloadSourceResolver.File("x",()->new ByteArrayInputStream(new byte[]{1}))),()->{})).isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(Path.of(System.getProperty("java.io.tmpdir"),"antv-download-center",id+".zip.part"))).isFalse();
    }
}
