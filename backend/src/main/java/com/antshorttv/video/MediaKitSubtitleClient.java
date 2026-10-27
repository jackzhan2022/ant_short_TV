package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaKitSubtitleClient {
    private final ObjectMapper json;
    private final String baseUrl, apiKey, queueId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public MediaKitSubtitleClient(ObjectMapper json,
        @Value("${ai.mediakit.base-url:${MEDIAKIT_BASE_URL:https://mediakit.cn-beijing.volces.com}}") String baseUrl,
        @Value("${ai.mediakit.api-key:${MEDIAKIT_API_KEY:}}") String apiKey,
        @Value("${ai.mediakit.queue-id:${MEDIAKIT_QUEUE_ID:}}") String queueId) {
        this.json=json; this.baseUrl=baseUrl.replaceAll("/+$", ""); this.apiKey=apiKey.trim(); this.queueId=queueId.trim();
    }
    public boolean configured() { return !apiKey.isBlank(); }
    public void requireConfigured() {
        if (!configured()) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"字幕擦除服务尚未配置，请联系管理员配置 AI MediaKit API Key。");
    }
    public String submit(String videoUrl, SubtitleEraseRequest settings, String token) {
        requireConfigured(); settings.validate();
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("video_url",videoUrl); body.put("client_token",token);
        if (!queueId.isBlank()) body.put("queue_id",queueId);
        boolean pro="PRO".equals(settings.quality());
        if (pro) {
            body.put("model_version","v5"); body.put("output_encode_mode","Quality");
            body.put("mode","REGION".equals(settings.method())?"Text":"Subtitle");
            if (settings.region()!=null) body.put("erase_ratio_location",List.of(settings.region().providerRegion()));
        }
        JsonNode result=send("POST","/api/v1/tools/erase-video-subtitle"+(pro?"-pro":""),body);
        String id=result.path("task_id").asText("");
        if (id.isBlank()) throw new IllegalStateException("字幕擦除服务未返回任务编号，请稍后重试查询。");
        return id;
    }
    public TaskResult poll(String taskId) {
        JsonNode body=send("GET","/api/v1/tasks/"+URLEncoder.encode(taskId,StandardCharsets.UTF_8),null);
        String status=body.path("status").asText("");
        if ("running".equals(status)) return new TaskResult(status,null,null,null,null,null);
        if ("failed".equals(status)) {
            String code=body.path("error").path("code").asText("TaskError").replaceAll("[^A-Za-z0-9_.-]", "");
            return new TaskResult(status,null,null,null,null,"字幕擦除失败（"+code+"），请检查视频或稍后重试。");
        }
        if (!"completed".equals(status)) throw new IllegalStateException("字幕擦除服务返回了无法识别的任务状态。");
        JsonNode result=body.path("result");
        String url=result.path("video_url").asText("");
        if (!httpUrl(url)) throw new IllegalStateException("字幕擦除服务未返回可下载的视频地址。");
        Instant now=Instant.now();
        long finished=body.path("finished_at").asLong(now.getEpochSecond());
        Instant expires=Instant.ofEpochSecond(Math.min(finished,now.getEpochSecond())).plus(Duration.ofHours(24));
        long explicit=body.path("expires_at").asLong(0);
        if (explicit>0 && Instant.ofEpochSecond(explicit).isBefore(expires)) expires=Instant.ofEpochSecond(explicit);
        BigDecimal duration=result.path("duration").isNumber()?result.path("duration").decimalValue():null;
        return new TaskResult(status,url,expires,duration,result.path("resolution").asText(null),null);
    }
    private JsonNode send(String method,String path,Map<String,Object> body) {
        requireConfigured();
        try {
            var builder=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(60))
                .header("Authorization","Bearer "+apiKey).header("Accept","application/json");
            if (body==null) builder.GET();
            else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()<200 || response.statusCode()>=300)
                throw new IllegalStateException("字幕擦除服务请求失败（HTTP "+response.statusCode()+"），请稍后重试。");
            JsonNode parsed=json.readTree(response.body());
            if (!parsed.path("success").asBoolean(false)) throw new IllegalStateException("字幕擦除服务未接受该请求，请检查服务配置或稍后重试。");
            return parsed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("字幕擦除请求已中断，请稍后重试。");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("字幕擦除服务连接异常，请稍后重试。");
        }
    }
    public long downloadTo(String url, java.nio.file.Path target) {
        if (!httpUrl(url)) throw new IllegalStateException("字幕擦除产物地址不可下载。");
        try {
            var downloadHttp=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            var response=downloadHttp.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
                HttpResponse.BodyHandlers.ofFile(target));
            long size=java.nio.file.Files.size(target);
            if(response.statusCode()<200 || response.statusCode()>=300 || size==0)
                throw new IllegalStateException("擦除结果下载失败，请重试转存。");
            return size;
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();throw new IllegalStateException("擦除结果转存已中断，可稍后重试。");
        } catch(java.io.IOException exception) {throw new IllegalStateException("擦除结果下载失败，可稍后重试转存。");}
    }
    static boolean httpUrl(String url) {
        try { URI uri=URI.create(url);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
            && uri.getHost()!=null && uri.getUserInfo()==null; } catch (IllegalArgumentException exception) { return false; }
    }
    public record TaskResult(String status,String videoUrl,Instant expiresAt,BigDecimal duration,String resolution,String error) {}
}
