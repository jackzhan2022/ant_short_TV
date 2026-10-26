package com.antshorttv.avatar;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Ark control-plane Assets API, signed with AK/SK (separate from inference API keys). */
@Component
public class ArkAssetClient {
    private final ObjectMapper json;
    private final String ak, sk, region, project;
    private final URI endpoint;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    public ArkAssetClient(ObjectMapper json,
        @Value("${ARK_ASSET_ACCESS_KEY:}") String ak, @Value("${ARK_ASSET_SECRET_KEY:}") String sk,
        @Value("${ARK_ASSET_REGION:cn-beijing}") String region,
        @Value("${ARK_ASSET_PROJECT_NAME:default}") String project,
        @Value("${ARK_ASSET_ENDPOINT:https://ark.cn-beijing.volcengineapi.com}") String endpoint) {
        this.json=json; this.ak=ak; this.sk=sk; this.region=region; this.project=project;
        this.endpoint=URI.create(endpoint.replaceAll("/+$", "")+"/");
    }
    public void requireConfigured() {
        if(ak.isBlank() || sk.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
            "请配置素材库 ARK_ASSET_ACCESS_KEY 和 ARK_ASSET_SECRET_KEY（AK/SK），推理 API Key 不能用于素材入库。");
    }
    public String project() { return project; }
    public String createGroup(String name) throws Exception {
        return id(call("CreateAssetGroup",Map.of("Name",limit(name,64),"GroupType","AIGC")));
    }
    public String createAsset(String group,String name,String url) throws Exception {
        return id(call("CreateAsset",Map.of("Name",limit(name,64),"GroupId",group,"AssetType","Image","URL",url)));
    }
    public Asset getAsset(String id) throws Exception { return asset(call("GetAsset",Map.of("Id",id))); }
    static Asset asset(JsonNode value) {
        String status=value.path("Status").asText();
        if(!Set.of("Active","Processing","Failed").contains(status)) throw new IllegalStateException("素材库返回了未知状态。");
        return new Asset(status,value.path("URL").asText(),value.path("Error").path("Code").asText(),
            value.path("Error").path("Message").asText());
    }
    public record Asset(String status,String url,String errorCode,String errorMessage) {}
    public static class ProviderException extends Exception {
        private final String code;
        private final boolean definitive;
        public ProviderException(String code,boolean definitive) {super("素材库请求失败（"+code+"）。");this.code=code;this.definitive=definitive;}
        public boolean definitive() {return definitive;}
        public String code() {return code;}
    }
    private JsonNode call(String action,Map<String,Object> fields) throws Exception {
        requireConfigured();
        var payload=new LinkedHashMap<>(fields);payload.put("ProjectName",project);
        byte[] body=json.writeValueAsBytes(payload);
        String query="Action="+action+"&Version=2024-01-01";
        String date=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        String sha=hash(body), headers="content-type;host;x-content-sha256;x-date";
        String canonical="POST\n/\n"+query+"\ncontent-type:application/json\nhost:"+endpoint.getAuthority()
            +"\nx-content-sha256:"+sha+"\nx-date:"+date+"\n\n"+headers+"\n"+sha;
        String scope=date.substring(0,8)+"/"+region+"/ark/request";
        byte[] key=hmac(hmac(hmac(hmac(sk.getBytes(StandardCharsets.UTF_8),date.substring(0,8)),region),"ark"),"request");
        String signature=HexFormat.of().formatHex(hmac(key,"HMAC-SHA256\n"+date+"\n"+scope+"\n"+hash(canonical.getBytes(StandardCharsets.UTF_8))));
        var request=HttpRequest.newBuilder(URI.create(endpoint+"?"+query)).timeout(Duration.ofSeconds(45))
            .header("Content-Type","application/json").header("X-Date",date).header("X-Content-Sha256",sha)
            .header("Authorization","HMAC-SHA256 Credential="+ak+"/"+scope+", SignedHeaders="+headers+", Signature="+signature)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        JsonNode root;
        try { root=json.readTree(response.body()); } catch(Exception e) {throw new ProviderException("INVALID_RESPONSE_"+response.statusCode(),false);}
        if(root==null) throw new ProviderException("EMPTY_RESPONSE",false);
        var error=root.path("ResponseMetadata").path("Error");
        if(response.statusCode()!=200 || !error.isMissingNode() && !error.isNull())
            throw new ProviderException(safeCode(error.path("Code").asText("HTTP_"+response.statusCode())),response.statusCode()<500 && !error.path("Code").asText().isBlank());
        return root.has("Result")?root.get("Result"):root;
    }
    static String safeCode(String code) {
        String safe=code.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return safe.isBlank()?"PROVIDER_FAILED":safe.substring(0,Math.min(safe.length(),120));
    }
    private static String id(JsonNode value) {
        String id=value.path("Id").asText();
        if(id.isBlank()) throw new IllegalStateException("素材库未返回资产 ID。");
        return id;
    }
    private static String limit(String s,int n) {return s.substring(0,Math.min(s.length(),n));}
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] hmac(byte[] key,String text) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
    }
}
