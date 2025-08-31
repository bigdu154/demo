package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SwaggerUiResourceConfig implements WebMvcConfigurer {
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // /swagger-ui/** 를 우리가 제공하는 리소스 위치로 먼저 매핑
    registry.addResourceHandler("/swagger-ui/**")
            .addResourceLocations("classpath:/META-INF/resources/swagger-ui/",  // 네가 넣은 커스텀 파일 위치
                                  "classpath:/static/swagger-ui/")              // (백업) static 위치도 함께
            .resourceChain(false);
  }
}