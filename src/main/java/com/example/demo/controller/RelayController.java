package com.example.demo.controller;

import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.*;

/**
 * A 서버가 인증을 끝낸 뒤 B 서버로 요청을 릴레이하고,
 * B의 응답을 그대로 클라이언트에 흘려보내는 컨트롤러.
 *
 * - URL: aHost/api/v1/**  →  bHost/api/v1/**
 * - 상태코드/헤더/바디 유지
 * - 스트리밍 전송 (대용량/Chunked 응답 적합)
 * - Swagger 문서/정적 오픈API/커스텀 프리픽스는 예외
 */
@RestController
@RequestMapping("${app.passthrough-prefix:/api}")
public class RelayController {

    @Value("${app.target-base-url}")
    private String targetBase; // e.g. https://bHost  (마지막 슬래시 X)

    // 릴레이에서 제거해야 할 hop-by-hop 헤더들 (RFC 7230)
    private static final Set<String> HOP_BY_HOP = new HashSet<>(Arrays.asList(
            "connection","keep-alive","proxy-authenticate","proxy-authorization",
            "te","trailer","transfer-encoding","upgrade"
    ));

    // 릴레이를 타면 안 되는 경로(prefix 기준) — 필요에 맞게 수정
    private static final List<String> EXCLUDED_PREFIXES = Arrays.asList(
        "/v3/api-docs",           // springdoc api docs
        "/swagger-ui",            // swagger ui
            "/ext/openapi-b.json",    // 외부 스펙 프록시/정적 경로
            "/api/v1/custom"          // 예: 네가 직접 만든 커스텀 API 프리픽스
    );

    private final CloseableHttpClient http;

    public RelayController() {
        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(60_000)
                .build();

        this.http = HttpClients.custom()
                .setDefaultRequestConfig(cfg)
                .setMaxConnTotal(200)
                .setMaxConnPerRoute(50)
                .build();
    }

    @PreDestroy
    public void close() throws Exception {
        http.close();
    }

    @RequestMapping(
            value = "/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD,
                      RequestMethod.OPTIONS})
    public ResponseEntity<StreamingResponseBody> relay(HttpServletRequest req) throws Exception {
        final String path = req.getRequestURI();      // ex) /api/v1/abc
        final String query = req.getQueryString();    // ex) x=1&y=2

        // 1) 예외 경로는 릴레이하지 않음 (스프링의 정확 매핑 우선 규칙 + 방어적 제외)
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }

        // 2) B 서버로 보낼 최종 URL (경로/쿼리 그대로 유지)
        final String targetUrl = targetBase + path + (query != null ? "?" + query : "");

        // 3) B로 나가는 요청 구성
        HttpRequestBase outbound = buildOutboundWithBodyIfNeeded(req, targetUrl);
        copyRequestHeaders(req, outbound);

        // 3-1) (선택) A→B 서비스 인증 토큰 주입 위치
        // outbound.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + issueServiceTokenForB());

        // 4) 실행 & 응답 스트리밍
        CloseableHttpResponse bRes = http.execute(outbound);

        HttpHeaders headers = new HttpHeaders();
        for (Header h : bRes.getAllHeaders()) {
            String lower = h.getName().toLowerCase(Locale.ROOT);
            if (!HOP_BY_HOP.contains(lower)) {
                headers.add(h.getName(), h.getValue());
            }
        }

        StreamingResponseBody body = os -> {
            try (InputStream is = (bRes.getEntity() != null) ? bRes.getEntity().getContent() : InputStream.nullInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            } finally {
                bRes.close();
            }
        };

        int status = bRes.getStatusLine().getStatusCode();
        return new ResponseEntity<>(body, headers, HttpStatus.valueOf(status));
    }

    private HttpRequestBase buildOutboundWithBodyIfNeeded(HttpServletRequest req, String url) throws Exception {
        String method = req.getMethod();
        switch (method) {
            case "GET":     return new HttpGet(url);
            case "DELETE":  return new HttpDelete(url);
            case "HEAD":    return new HttpHead(url);
            case "OPTIONS": return new HttpOptions(url);
            case "POST":
            case "PUT":
            case "PATCH": {
                HttpEntityEnclosingRequestBase r =
                        method.equals("POST") ? new HttpPost(url) :
                        method.equals("PUT")  ? new HttpPut(url)  : new HttpPatch(url);
                byte[] body = req.getInputStream().readAllBytes();
                if (body.length > 0) {
                    r.setEntity(new ByteArrayEntity(body));
                }
                return r;
            }
            default:
                throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    private void copyRequestHeaders(HttpServletRequest req, HttpRequestBase out) {
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP.contains(lower) || "host".equals(lower)) continue;

            Enumeration<String> values = req.getHeaders(name);
            while (values.hasMoreElements()) {
                out.addHeader(name, values.nextElement());
            }
        }
    }

    // (예시) B용 서비스 토큰 발급/캐시 로직을 여기에 구현
    // private String issueServiceTokenForB() { ... }
}
