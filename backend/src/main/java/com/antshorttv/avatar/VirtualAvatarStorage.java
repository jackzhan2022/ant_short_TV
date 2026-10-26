package com.antshorttv.avatar;

import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class VirtualAvatarStorage {
    private final ObjectStorageService storage;
    private final ObjectStorageKeyFactory keys;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    public VirtualAvatarStorage(ObjectStorageService storage,ObjectStorageKeyFactory keys) {this.storage=storage;this.keys=keys;}
    public String sourceUrl(String path,String url) {
        if(path!=null && !path.isBlank()) return storage.modelAccessUrl(path,Duration.ofHours(24));
        URI uri=URI.create(url);
        if(!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) throw new IllegalArgumentException("源图片需要公共 HTTP 地址。");
        return url;
    }
    public String save(long tenant,long project,long task,String url) throws Exception {
        URI uri=URI.create(url);
        String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
        if(!"https".equals(uri.getScheme()) || !(host.endsWith(".volces.com") || host.endsWith(".byteimg.com")))
            throw new IllegalArgumentException("素材结果地址不是受支持的火山存储地址。");
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45)).GET().build();
        var bytes=new java.io.ByteArrayOutputStream();
        var future=http.sendAsync(request,HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk->chunk.ifPresent(part->{
            if(bytes.size()+part.length>30*1024*1024) throw new IllegalStateException("素材结果超过 30 MB。");
            bytes.writeBytes(part);
        })));
        HttpResponse<Void> response;
        try {response=future.get(60,java.util.concurrent.TimeUnit.SECONDS);}
        finally {if(!future.isDone()) future.cancel(true);}
        if(response.statusCode()!=200 || bytes.size()==0) throw new IllegalStateException("素材结果下载失败。");
        String mime=response.headers().firstValue("Content-Type").orElse("application/octet-stream").split(";")[0];
        if(!mime.startsWith("image/")) throw new IllegalStateException("素材结果不是图片。");
        String extension=java.util.Map.of("image/png","png","image/jpeg","jpg","image/webp","webp",
            "image/gif","gif","image/tiff","tiff","image/heic","heic","image/heif","heif").getOrDefault(mime,"img");
        String path=keys.projectOriginal(tenant,project,"virtual-avatars",task,"v1",LocalDate.now(),extension);
        storage.uploadOriginal(path,bytes.toByteArray(),mime);
        return path;
    }
}
