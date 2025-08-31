package com.example.demo.controller;

import com.example.demo.config.ApiSpecProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.stream.Collectors;

@RestController
public class DocsUiController {

    private final ApiSpecProperties props;
    private final ObjectMapper om = new ObjectMapper();

    public DocsUiController(ApiSpecProperties props) {
        this.props = props;
    }

    // 단건 문서
    @GetMapping({"/docs/{name}", "/docs/{name}/swagger.html"})
    public ResponseEntity<Void> openSingle(@PathVariable String name) {
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create("/swagger-ui/index.html?configUrl=/swagger-config/single/" + name));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }

    // 그룹 문서
    @GetMapping({"/docs/group/{group}", "/docs/group/{group}/swagger.html"})
    public ResponseEntity<Void> openGroup(@PathVariable String group) {
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create("/swagger-ui/index.html?configUrl=/swagger-config/group/" + group));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }

    // Swagger UI 설정(JSON) - 단건
    @GetMapping(value = "/swagger-config/single/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> configSingle(@PathVariable String name) {
        ObjectNode root = om.createObjectNode();
        ArrayNode urls = om.createArrayNode();
        ObjectNode e = om.createObjectNode();
        e.put("name", name.toUpperCase());
        e.put("url", "/external-specs/" + name);
        urls.add(e);
        root.set("urls", urls);
        root.put("urlsPrimaryName", name.toUpperCase());
        // (옵션) UI 옵션
        root.put("layout", "StandaloneLayout");
        root.put("deepLinking", true);
        root.put("docExpansion", "none");
        return ResponseEntity.ok(root.toString());
    }

    // Swagger UI 설정(JSON) - 그룹
    @GetMapping(value = "/swagger-config/group/{group}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> configGroup(@PathVariable String group) {
        var apis = props.getApis().stream()
                .filter(a -> group.equalsIgnoreCase(a.getGroup()))
                .collect(Collectors.toList());

        ObjectNode root = om.createObjectNode();
        ArrayNode urls = om.createArrayNode();
        for (var a : apis) {
            ObjectNode e = om.createObjectNode();
            e.put("name", a.getName());
            e.put("url", "/external-specs/" + a.getName());
            urls.add(e);
        }
        root.set("urls", urls);
        if (!apis.isEmpty()) root.put("urlsPrimaryName", apis.get(0).getName());
        root.put("layout", "StandaloneLayout");
        root.put("deepLinking", true);
        root.put("docExpansion", "none");
        return ResponseEntity.ok(root.toString());
    }
}
