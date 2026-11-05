package com.antshorttv.operations;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record OperationsQuery(Instant from,Instant to,int pageSize,String cursor,Map<String,Object> dimensions) {
    private static final Set<String> KEYS=Set.of("from","to","pageSize","cursor","tenantId","projectId","providerId","modelId","scene","instanceId");
    public static OperationsQuery parse(Map<String,String> params,Instant now) {
        try {
            if(!KEYS.containsAll(params.keySet())) invalid();
            Instant to=params.containsKey("to")?Instant.parse(params.get("to")):now;
            Instant from=params.containsKey("from")?Instant.parse(params.get("from")):to.minusSeconds(3600);
            if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(90))>0||to.isAfter(now.plusSeconds(60))) invalid();
            int page=Integer.parseInt(params.getOrDefault("pageSize","20"));if(page<1||page>100)invalid();
            var dimensions=new LinkedHashMap<String,Object>();
            for(String key:KEYS) if(!Set.of("from","to","pageSize","cursor").contains(key)&&params.containsKey(key)) {
                String value=params.get(key);if(value==null||value.isBlank()||value.length()>128)invalid();
                if(key.endsWith("Id")&&!key.equals("instanceId")) {long id=Long.parseLong(value);if(id<1)invalid();dimensions.put(key,id);}
                else dimensions.put(key,value);
            }
            String cursor=params.get("cursor");if(cursor!=null&&cursor.length()>200)invalid();
            return new OperationsQuery(from,to,page,cursor,Map.copyOf(dimensions));
        } catch(java.time.DateTimeException|NumberFormatException e) {invalid();return null;}
    }
    private static void invalid() {throw new BusinessException(ErrorCode.VALIDATION_ERROR,"监控筛选参数不正确；时间范围最多90天，每页最多100条。");}
}
