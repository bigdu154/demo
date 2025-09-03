package com.example.demo.config;

import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SanityPingConfig {
  public SanityPingConfig() { System.out.println("### SanityPingConfig loaded"); }

  @Bean
  public OpenApiCustomiser ping() {
    System.out.println("### OpenApiCustomiser bean created");
    return openAPI -> System.out.println("### OpenApiCustomiser CALLED"); // ← 반드시 찍혀야 함
  }
}
