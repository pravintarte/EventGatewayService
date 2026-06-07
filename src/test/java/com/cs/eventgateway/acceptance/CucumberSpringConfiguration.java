package com.cs.eventgateway.acceptance;

import com.cs.eventgateway.client.AccountServiceClient;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Spring Boot test context used by Cucumber step definitions.
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "event-gateway.retry.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(CucumberSpringConfiguration.MockAccountServiceConfig.class)
class CucumberSpringConfiguration {

    @TestConfiguration
    static class MockAccountServiceConfig {

        @Bean
        @Primary
        AccountServiceClient accountServiceClient() {
            return mock(AccountServiceClient.class);
        }
    }
}
