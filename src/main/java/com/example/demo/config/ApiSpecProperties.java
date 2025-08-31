package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "relay")
public class ApiSpecProperties {

    private List<Api> apis;

    public static class Api {
        private String name;
        private String group;
        private String baseUrl;
        private String specUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getSpecUrl() { return specUrl; }
        public void setSpecUrl(String specUrl) { this.specUrl = specUrl; }
    }

    public List<Api> getApis() { return apis; }
    public void setApis(List<Api> apis) { this.apis = apis; }
}
