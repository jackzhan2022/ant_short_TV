package com.antshorttv.drama;

import com.antshorttv.security.JsonAccessDeniedHandler;
import com.antshorttv.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class MiniappSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain miniappSecurityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint,
        JsonAccessDeniedHandler denied) throws Exception {
        // 独立 Bearer 会话在控制器校验；不能接受后台 Cookie 身份，也不写浏览器 Cookie。
        return http.securityMatcher("/api/miniapp/**").cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
}
