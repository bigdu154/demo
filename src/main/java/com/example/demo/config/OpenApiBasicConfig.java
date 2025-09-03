package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiBasicConfig {

    @Value("${app.target-base-url:}")
    private String serverUrl;

    @Bean
    public OpenAPI baseOpenAPI() {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("A Server API")
                        .version("v1")
                        .description("A 서버 커스텀 API와 B 서버 스펙 병합 문서")
                        .contact(new Contact().name("Platform Team").email("platform@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/")))
                .externalDocs(new ExternalDocumentation()
                        .description("Internal Docs")
                        .url("https://wiki.example.com"));

        // (선택) Swagger Try it out 기본 서버 URL 고정
        if (!serverUrl.isEmpty()) {
            api.setServers(List.of(new Server().url(serverUrl)));
        }

        return api;
    }
}
