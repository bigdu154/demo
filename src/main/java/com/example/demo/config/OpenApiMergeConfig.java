package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class OpenApiMergeConfig {

  private static final Logger log = LoggerFactory.getLogger(OpenApiMergeConfig.class);

  @Value("${app.openapi-url}") private String externalSpecUrl;
  @Value("${app.openapi.tag-prefix:[B] }")   private String tagPrefix;
  @Value("${app.openapi.path-prefix:}")     private String pathPrefix;
  @Value("${app.openapi.prefer-local:true}") private boolean preferLocal;

  private final RestTemplate rt = new RestTemplate();

  @Bean
  public OpenApiCustomiser mergeExternalSpec() {
    return (OpenAPI local) -> {
      try {
        System.out.println("externalSpecUrl: " + externalSpecUrl);
        log.info("[OpenAPI-Merge] fetching external spec: {}", externalSpecUrl);
        ResponseEntity<String> res = rt.exchange(externalSpecUrl, HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()), String.class);

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
          log.warn("[OpenAPI-Merge] external fetch failed: status={}", res.getStatusCode());
          return;
        }

        OpenAPI external = new OpenAPIV3Parser().readContents(res.getBody(), null, null).getOpenAPI();
        if (external == null) {
          log.warn("[OpenAPI-Merge] parser returned null OpenAPI");
          return;
        }

        int before = local.getPaths() == null ? 0 : local.getPaths().size();
        mergeTags(local, external);
        mergePaths(local, external);
        mergeComponents(local, external);
        local.setServers(List.of(new Server().url("/")));
        int after = local.getPaths() == null ? 0 : local.getPaths().size();
        log.info("[OpenAPI-Merge] merged ok. paths: {} -> {}", before, after);

      } catch (Exception e) {
        log.error("[OpenAPI-Merge] failed to merge external spec", e);
      }
    };
  }

  @Bean
  public GroupedOpenApi defaultGroup(OpenApiCustomiser mergeExternalSpec) {
      return GroupedOpenApi.builder()
              .group("default")          // Swagger UI가 쓰는 기본 그룹
              .pathsToMatch("/**")       // 전부 포함
              .addOpenApiCustomiser(mergeExternalSpec) // ← 강제 실행 지점
              .build();
  }

  private void mergeTags(OpenAPI local, OpenAPI external) {
    if (external.getTags() == null) return;
    List<Tag> merged = Optional.ofNullable(local.getTags()).orElse(new ArrayList<>());
    Set<String> names = merged.stream().map(Tag::getName).collect(Collectors.toSet());

    for (Tag t : external.getTags()) {
      String newName = tagPrefix + t.getName();
      if (!names.contains(newName)) {
        merged.add(new Tag().name(newName).description(t.getDescription()));
        names.add(newName);
      }
    }
    local.setTags(merged);
  }

  private void mergePaths(OpenAPI local, OpenAPI external) {
    Paths localPaths = Optional.ofNullable(local.getPaths()).orElse(new Paths());
    Paths extPaths   = Optional.ofNullable(external.getPaths()).orElse(new Paths());

    extPaths.forEach((raw, item) -> {
      String path = (pathPrefix + raw).replaceAll("//+", "/");
      if (localPaths.containsKey(path) && preferLocal) return;

      if (item.readOperations() != null) {
        item.readOperations().forEach(op -> {
          List<String> tags = Optional.ofNullable(op.getTags()).orElse(new ArrayList<>());
          op.setTags(tags.stream().map(t -> tagPrefix + t).collect(Collectors.toList()));
        });
      }
      localPaths.addPathItem(path, item);
    });

    local.setPaths(localPaths);
  }

  private void mergeComponents(OpenAPI local, OpenAPI external) {
    if (external.getComponents() == null) return;
    if (local.getComponents() == null) {
      local.setComponents(new io.swagger.v3.oas.models.Components());
    }
    Map<String, io.swagger.v3.oas.models.media.Schema> extSchemas =
        Optional.ofNullable(external.getComponents().getSchemas()).orElse(Collections.emptyMap());
    Map<String, io.swagger.v3.oas.models.media.Schema> locSchemas =
        Optional.ofNullable(local.getComponents().getSchemas()).orElse(new LinkedHashMap<>());

    for (Map.Entry<String, io.swagger.v3.oas.models.media.Schema> e : extSchemas.entrySet()) {
      String name = e.getKey();
      String newName = locSchemas.containsKey(name) ? ("B_" + name) : name;
      locSchemas.putIfAbsent(newName, e.getValue());
    }
    local.getComponents().setSchemas(locSchemas);

    // 필요하면 responses/parameters/requestBodies/securitySchemes 등도 같은 방식으로 추가
  }
}
