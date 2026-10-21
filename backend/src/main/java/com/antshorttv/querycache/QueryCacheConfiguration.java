package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class QueryCacheConfiguration {
    @Bean(destroyMethod="close")
    @ConditionalOnProperty(name="query-cache.enabled",havingValue="true")
    CacheStore queryCacheStore(@Value("${spring.data.redis.host:127.0.0.1}") String host,
        @Value("${spring.data.redis.port:6379}") int port,
        @Value("${spring.data.redis.username:}") String user,
        @Value("${spring.data.redis.password:}") String password,
        @Value("${spring.data.redis.database:0}") int database) {
        return new LettuceCacheStore(host,port,user,password,database);
    }
    @Bean
    QueryResultCache queryResultCache(org.springframework.beans.factory.ObjectProvider<CacheStore> stores,
        ObjectMapper mapper,MeterRegistry metrics,@Value("${query-cache.enabled:false}") boolean enabled,
        @Value("${query-cache.secret-key:${ai.secret-key:}}") String secret) {
        if(!enabled)return QueryResultCache.disabled();
        if(secret.isBlank())throw new IllegalStateException("Query cache encryption key must be configured");
        return new QueryResultCache(stores.getObject(),mapper,new AiSecretCodec("antv-query-cache-v1:"+secret),metrics,true);
    }
}
