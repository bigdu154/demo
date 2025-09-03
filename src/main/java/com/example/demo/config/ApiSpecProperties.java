package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "relay")
public class ApiSpecProperties {

    private String baseUrl;
    private String specUrl;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getSpecUrl() { return specUrl; }
    public void setSpecUrl(String specUrl) { this.specUrl = specUrl; }

}
