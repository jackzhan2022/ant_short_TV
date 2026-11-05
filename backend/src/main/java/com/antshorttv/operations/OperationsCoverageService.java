package com.antshorttv.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

/** Only the writer can certify an interval after its family data has committed; resource presence is not proof. */
@Service
public class OperationsCoverageService {
    private static final String PREFIX="telemetry.coverage.";
    private static final Set<String> FAMILIES=Set.of("HTTP","RESOURCE","AI");
    private final OperationsDatabase database;
    private final ObjectMapper json;
    public OperationsCoverageService(OperationsDatabase database,ObjectMapper json) { this.database=database;this.json=json; }

    public void recordRange(String family,String instanceId,String bootId,Instant from,Instant through,Instant now) {
        validate(family,instanceId,bootId);
        Instant begin=ceil(from),end=through.truncatedTo(ChronoUnit.MINUTES);
        Instant closed=now.minusSeconds(10).truncatedTo(ChronoUnit.MINUTES);
        if(end.isAfter(closed))end=closed;
        Instant finalEnd=end;
        database.transactions().executeWithoutResult(transaction -> {
            String key=key(family,instanceId);
            var previous=locked(key,now);
            Instant reset=previous==null?null:previous.resetAt;
            Instant collision=previous==null?null:previous.collisionUntil;
            Instant start=begin;
            if(previous!=null && !previous.bootId.equals(bootId)) {
                reset=ceil(now);
                if(!family.equals("AI") && !previous.updatedAt.isBefore(now.minusSeconds(120)))collision=now.plusSeconds(120);
            }
            if(reset!=null && start.isBefore(reset))start=reset;
            if(previous!=null && previous.bootId.equals(bootId) && previous.healthy
                && !start.isAfter(previous.through) && !finalEnd.isBefore(previous.from)) {
                start=start.isBefore(previous.from)?start:previous.from;
                if(reset!=null && start.isBefore(reset))start=reset;
            }
            Instant finish=finalEnd;
            if(previous!=null && previous.bootId.equals(bootId) && previous.healthy
                && !begin.isAfter(previous.through) && !finalEnd.isBefore(previous.from)
                && previous.through.isAfter(finish))finish=previous.through;
            write(key,new Marker(family,instanceId,bootId,start,finish,reset,collision,finish.isAfter(start),now));
        });
    }

    /** A dropped or ambiguous interval cannot be certified by replaying an old in-memory starting point. */
    public void invalidate(String family,String instanceId,String bootId,Instant now) {
        validate(family,instanceId,bootId);
        database.transactions().executeWithoutResult(transaction -> {
            String key=key(family,instanceId);var previous=locked(key,now);Instant barrier=ceil(now);
            write(key,new Marker(family,instanceId,bootId,barrier,barrier,barrier,
                previous==null?null:previous.collisionUntil,false,now));
        });
    }

    public boolean complete(String metric,Map<String,Object> dimensions,int windowMinutes,Instant end) {
        String family=metric.startsWith("http.")?"HTTP":metric.startsWith("ai.")?"AI"
            :Set.of("db.pending","jvm.heapPercent").contains(metric)?"RESOURCE":null;
        if(family==null || windowMinutes<1 || windowMinutes>1440 || end==null)return false;
        try {
            if(family.equals("AI") && dimensions.containsKey("instanceId"))return false;
            String selected=family.equals("AI")?"GLOBAL":dimensions.get("instanceId")==null?null:String.valueOf(dimensions.get("instanceId"));
            var markers=markers(family,selected);
            if(markers.isEmpty())return false;
            Set<String> expected=new HashSet<>(markers.keySet());
            // A newly seen resource collector must also certify its HTTP family before global HTTP can be complete.
            if(family.equals("HTTP") && selected==null)expected.addAll(markers("RESOURCE",null).keySet());
            Instant from=end.minusSeconds(windowMinutes*60L);
            for(String instance:expected) {
                Marker marker=markers.get(instance);
                if(marker==null || !marker.healthy || marker.from.isAfter(from) || marker.through.isBefore(end)
                    || marker.updatedAt.isBefore(end.minusSeconds(90))
                    || (marker.collisionUntil!=null && marker.collisionUntil.isAfter(end)))return false;
            }
            return true;
        }catch(RuntimeException unavailable) { return false; }
    }

    private Map<String,Marker> markers(String family,String selected) {
        List<Map<String,Object>> rows;
        if(selected!=null)rows=database.jdbc().queryForList("select value_json,updated_at from ops_runtime_setting where setting_key=?",key(family,selected));
        // Known collectors remain required until operators explicitly change the rule scope.
        else rows=database.jdbc().queryForList("select value_json,updated_at from ops_runtime_setting where setting_key like ? order by updated_at desc limit 1001",
            PREFIX+family+".%");
        if(rows.size()>1000)throw new IllegalStateException("Too many operations collectors");
        var result=new HashMap<String,Marker>();
        for(var row:rows){Marker marker=decode(String.valueOf(row.get("value_json")),OperationsTime.utc(row.get("updated_at")));result.put(marker.instanceId,marker);}
        return result;
    }
    private Marker locked(String key,Instant now) {
        database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values(?,'{}',0,?) on duplicate key update setting_key=values(setting_key)",key,Timestamp.from(now));
        var row=database.jdbc().queryForMap("select value_json,updated_at from ops_runtime_setting where setting_key=? for update",key);
        String value=String.valueOf(row.get("value_json"));
        return value.equals("{}")?null:decode(value,OperationsTime.utc(row.get("updated_at")));
    }
    private record Marker(String family,String instanceId,String bootId,Instant from,Instant through,Instant resetAt,Instant collisionUntil,boolean healthy,Instant updatedAt) {}
    private Marker decode(String value,Instant updatedAt) {
        try {
            JsonNode node=json.readTree(value);
            return new Marker(node.path("family").asText(),node.path("instanceId").asText(),node.path("bootId").asText(),
                Instant.parse(node.path("coverageFrom").asText()),Instant.parse(node.path("finalizedThrough").asText()),
                optional(node,"resetAt"),optional(node,"collisionUntil"),node.path("healthy").asBoolean(false),updatedAt);
        }catch(Exception corrupt){throw new IllegalStateException("Invalid finalized coverage marker",corrupt);}
    }
    private void write(String key,Marker marker) {
        var value=new LinkedHashMap<String,Object>();value.put("family",marker.family);value.put("instanceId",marker.instanceId);value.put("bootId",marker.bootId);
        value.put("coverageFrom",marker.from.toString());value.put("finalizedThrough",marker.through.toString());value.put("healthy",marker.healthy);
        value.put("resetAt",marker.resetAt==null?null:marker.resetAt.toString());value.put("collisionUntil",marker.collisionUntil==null?null:marker.collisionUntil.toString());
        try{database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key=?",json.writeValueAsString(value),Timestamp.from(marker.updatedAt),key);}
        catch(com.fasterxml.jackson.core.JsonProcessingException impossible){throw new IllegalStateException(impossible);}
    }
    private static Instant optional(JsonNode node,String field){return node.path(field).isTextual()?Instant.parse(node.path(field).asText()):null;}
    private static Instant ceil(Instant at){Instant floor=at.truncatedTo(ChronoUnit.MINUTES);return at.equals(floor)?floor:floor.plusSeconds(60);}
    private static String key(String family,String instanceId) {
        try{return PREFIX+family+"."+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(instanceId.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static void validate(String family,String instance,String boot) {
        if(!FAMILIES.contains(family)||instance==null||instance.isBlank()||instance.length()>128||boot==null||boot.isBlank()||boot.length()>64)
            throw new IllegalArgumentException("Invalid operations coverage collector");
    }
}
