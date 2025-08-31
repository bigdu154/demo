package com.example.demo.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerUiInitializerController {

  @GetMapping(value = "/swagger-ui/swagger-initializer.js", produces = "application/javascript")
  public ResponseEntity<ClassPathResource> init() {
    // 커스텀 파일을 명시적으로 리턴
    ClassPathResource resource = new ClassPathResource("META-INF/resources/swagger-ui/swagger-initializer.js");
    return ResponseEntity.ok().contentType(MediaType.valueOf("application/javascript")).body(resource);
  }
}