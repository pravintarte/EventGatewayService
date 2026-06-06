package com.eventgateway.api.controller;

import com.eventgateway.api.dto.HelloResponse;
import com.eventgateway.api.service.HelloService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing basic readiness-style endpoints for the Event Gateway API.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HelloController {

    private final HelloService helloService;

    /**
     * Returns a simple response proving that the service is running.
     *
     * @return hello-world service metadata
     */
    @GetMapping("/hello")
    public ResponseEntity<HelloResponse> hello() {
        log.info("Received hello request");
        HelloResponse response = helloService.getHelloResponse();
        log.debug("Returning hello response: {}", response);
        return ResponseEntity.ok(response);
    }
}
