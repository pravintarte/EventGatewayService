package com.cs.eventgateway;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootstrap class for the Event Gateway API microservice.
 *
 * <p>The application starts a Spring Boot web service and logs the active runtime
 * profile set so deployment environments can be verified from startup logs.</p>
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class EventGatewayApiApplication {

    /**
     * Starts the Event Gateway API service.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(EventGatewayApiApplication.class, args);
        log.info("Event Gateway API started with active profiles: {}",
                Arrays.toString(context.getEnvironment().getActiveProfiles()));
    }
}
