package com.example.demo.controller;

import com.example.demo.config.ApiSpecProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/external-specs")
public class ExternalSpecRewriteController {

    private final RestTemplate rt = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();
    private final ApiSpecProperties props;

    public ExternalSpecRewriteController(ApiSpecProperties props) {
        this.props = props;
    }

    /**
     * 외부 스펙을 우리 서버용으로 재작성:
     *  - OAS3: servers = http(s)://host[:port], paths에 "/{name}" 프리픽스(※ relay 없음)
     *  - Swagger v2: host, basePath = "/{name}" (paths에는 프리픽스 X)
     *  - OAS 3.1.x → 3.0.3 보정(구 UI 호환)
     */
    @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> rewrittenSpec(@PathVariable String name, HttpServletRequest req) {
        var api = props.getApis().stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (api == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Unknown API name: " + name).getBytes());
        }

        ResponseEntity<byte[]> res = rt.exchange(
                RequestEntity.get(URI.create(api.getSpecUrl()))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.ALL_VALUE)
                        .build(),
                byte[].class);

        byte[] body = Optional.ofNullable(res.getBody()).orElse(new byte[0]);

        try {
            JsonNode root = om.readTree(body);

            // http(s)://host[:port]
            String scheme = req.getScheme();
            String host = req.getServerName();
            int port = req.getServerPort();
            String baseOrigin = scheme + "://" + host + ((port == 80 || port == 443) ? "" : ":" + port);

            final String namePrefix = "/" + name; // ← 여기! relay 대신 이름

            if (root.has("openapi")) {
                // ===== OpenAPI 3.x =====
                ObjectNode oas = (ObjectNode) root;

                String ver = oas.path("openapi").asText("");
                if (ver.startsWith("3.1")) {
                    oas.put("openapi", "3.0.3");
                }

                // servers: baseOrigin (prefix는 paths에만)
                ArrayNode servers = om.createArrayNode();
                ObjectNode server0 = om.createObjectNode();
                server0.put("url", baseOrigin);
                servers.add(server0);
                oas.set("servers", servers);

                // paths 키에 "/{name}" 프리픽스 1회 적용
                ObjectNode paths = (ObjectNode) oas.path("paths");
                if (paths != null && paths.size() > 0) {
                    ObjectNode newPaths = om.createObjectNode();
                    paths.fieldNames().forEachRemaining(p -> {
                        String withPrefix = p.startsWith(namePrefix) ? p
                                : namePrefix + (p.startsWith("/") ? p : "/" + p);
                        newPaths.set(withPrefix, paths.get(p));
                    });
                    oas.set("paths", newPaths);
                }

            } else if (root.has("swagger")) {
                // ===== Swagger 2.0 =====
                ObjectNode v2 = (ObjectNode) root;
                v2.put("swagger", "2.0");

                String hostPort = host + ((port == 80 || port == 443) ? "" : ":" + port);
                v2.put("host", hostPort);
                v2.put("basePath", namePrefix); // ← "/{name}"

                ArrayNode schemes = om.createArrayNode();
                schemes.add(scheme);
                v2.set("schemes", schemes);

                // v2는 basePath가 있으므로 paths에 프리픽스 추가 X
            } else {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Upstream is not a valid OpenAPI/Swagger document".getBytes());
            }

            byte[] out = om.writeValueAsBytes(root);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(out);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Failed to process upstream spec: " + e.getMessage()).getBytes());
        }
    }
}
