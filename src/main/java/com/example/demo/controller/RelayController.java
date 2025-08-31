package com.example.demo.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;

import com.example.demo.config.ApiSpecProperties;

@RestController
public class RelayController {

        private final RestTemplate rt = new RestTemplate();
        private final ApiSpecProperties props;

        public RelayController(ApiSpecProperties props) {
                this.props = props;
        }

        /**
         * relay 없이: "/{name}/**" 로 바로 프록시
         * 예) /abc/posts → baseUrl/posts
         */
        @RequestMapping(value = "/{name:^(?!swagger-ui$|swagger-ui\\.html$|v3$|external-specs$|swagger-config$|docs$|actuator$|error$|favicon\\.ico$).+}/**", method = {
                        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD
        })
        public ResponseEntity<byte[]> proxy(@PathVariable String name,
                        HttpMethod method,
                        @RequestBody(required = false) byte[] body,
                        HttpServletRequest req,
                        @RequestHeader HttpHeaders inHeaders) {

                var api = props.getApis().stream()
                                .filter(a -> a.getName().equalsIgnoreCase(name))
                                .findFirst()
                                .orElse(null);
                if (api == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body(("Unknown API name: " + name).getBytes());
                }

                String requestUri = req.getRequestURI(); // e.g. "" or "/abc/api/posts/posts"
                String contextPath = Optional.ofNullable(req.getContextPath()).orElse(""); // e.g. "" or "/app"

                String mount = contextPath + "/" + name; // e.g. "/abc" (contextPath가 있으면 "/app/abc")
                String remainder;
                if (requestUri.equals(mount)) {
                        remainder = ""; // 정확히 "/{name}" 로 끝나는 경우
                } else if (requestUri.startsWith(mount + "/")) {
                        remainder = requestUri.substring((mount + "/").length()); // "api/posts/posts"
                } else {
                        // 예외 상황: 패턴이 예상과 다를 때 최후의 방어
                        remainder = requestUri;
                        if (remainder.startsWith("/"))
                                remainder = remainder.substring(1);
                }

                // 업스트림 URL 안전하게 조립
                String targetBase = trimTrailingSlash(api.getBaseUrl()); // e.g. https://api.example.com
                String targetUrl = remainder.isEmpty() ? targetBase : targetBase + "/" + remainder;
                // 쿼리 보존
                if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
                        targetUrl = targetUrl + "?" + req.getQueryString();
                }

                // 요청 헤더: Hop-by-Hop 제거 + Host 제거 (RestTemplate가 채움)
                HttpHeaders outHeaders = new HttpHeaders();
                inHeaders.forEach((k, v) -> {
                        if (!isHopByHop(k) && !HttpHeaders.HOST.equalsIgnoreCase(k)) {
                                outHeaders.put(k, v);
                        }
                });

                // X-Forwarded-* 추가 (업스트림에서 원 요청 맥락 파악 가능)
                outHeaders.set("X-Forwarded-Host", req.getServerName()
                                + ((req.getServerPort() == 80 || req.getServerPort() == 443) ? ""
                                                : ":" + req.getServerPort()));
                outHeaders.set("X-Forwarded-Proto", req.getScheme());
                outHeaders.set("X-Forwarded-For", Optional.ofNullable(req.getHeader("X-Forwarded-For"))
                                .map(x -> x + ", " + req.getRemoteAddr()).orElse(req.getRemoteAddr()));

                // RestTemplate 타임아웃(선택): 생성자에서 세팅했다면 생략 가능
                // (필요 시 Bean으로 HttpComponentsClientHttpRequestFactory 설정 권장)

                ResponseEntity<byte[]> upstreamResp;
                try {
                        upstreamResp = rt.exchange(URI.create(targetUrl), method,
                                        new HttpEntity<>(body, outHeaders), byte[].class);
                } catch (Exception ex) {
                        // 업스트림 연결 실패 등
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                        .contentType(MediaType.TEXT_PLAIN)
                                        .body(("Upstream request failed: " + ex.getClass().getSimpleName() + " - "
                                                        + ex.getMessage()).getBytes());
                }

                // 응답 헤더: Hop-by-Hop 제거 + Location 리라이트(리다이렉트 등)
                HttpHeaders respHeaders = new HttpHeaders();
                upstreamResp.getHeaders().forEach((k, v) -> {
                        if (!isHopByHop(k)) {
                                respHeaders.put(k, v);
                        }
                });

                // Location 헤더가 업스트림 절대경로면 → 프록시 경유 경로로 바꿔주기 (/ {name} / …)
                rewriteLocationHeader(respHeaders, api.getBaseUrl(), name);

                return new ResponseEntity<>(upstreamResp.getBody(), respHeaders, upstreamResp.getStatusCode());
        }

        /* === helpers === */

        private static boolean isHopByHop(String headerName) {
                String h = headerName.toLowerCase();
                return h.equals("connection")
                                || h.equals("keep-alive")
                                || h.equals("proxy-authenticate")
                                || h.equals("proxy-authorization")
                                || h.equals("te")
                                || h.equals("trailer")
                                || h.equals("transfer-encoding")
                                || h.equals("upgrade");
        }

        private static String trimTrailingSlash(String s) {
                if (s == null || s.isEmpty())
                        return s;
                int i = s.length();
                while (i > 0 && s.charAt(i - 1) == '/')
                        i--;
                return (i == s.length()) ? s : s.substring(0, i);
        }

        private static void rewriteLocationHeader(HttpHeaders headers, String upstreamBase, String name) {
                if (!headers.containsKey(HttpHeaders.LOCATION))
                        return;
                String loc = headers.getFirst(HttpHeaders.LOCATION);
                if (loc == null || loc.isBlank())
                        return;

                String base = trimTrailingSlash(upstreamBase); // e.g. https://api.example.com
                try {
                        URI locUri = URI.create(loc);
                        // 업스트림 절대 URL인지 판단
                        if (locUri.isAbsolute()) {
                                // 업스트림 base로 시작하면 → 프록시 경로로 치환
                                // ex) https://api.example.com/foo -> /{name}/foo
                                String locStr = locUri.toString();
                                if (locStr.startsWith(base + "/") || locStr.equals(base)) {
                                        String remainder = locStr.substring(base.length()); // leading "/" 포함 가능
                                        if (remainder.isEmpty())
                                                remainder = "/";
                                        String proxyPath = "/" + name
                                                        + (remainder.startsWith("/") ? remainder : "/" + remainder);
                                        headers.set(HttpHeaders.LOCATION, proxyPath);
                                }
                        }
                } catch (IllegalArgumentException ignore) {
                        // 잘못된 Location이면 패스
                }
        }

}
