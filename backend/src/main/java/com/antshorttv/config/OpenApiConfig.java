package com.antshorttv.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI antShortTvOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Ant Short TV API")
                .version("0.1.0")
                .description("Backend API for the Ant Short TV admin console."));
    }
}
