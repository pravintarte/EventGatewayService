package com.cs.eventgateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "event-gateway.retry.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.zipkin.autoconfigure.ZipkinAutoConfiguration"
})
@AutoConfigureMockMvc
class EventTimestampValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void submitEvent_whenEventTimestampHasNonUtcOffset_returnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "7fe118d2-1958-4238-8647-a52c55b946c8",
                                  "accountId": "acct-123",
                                  "type": "CREDIT",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T09:02:11-05:00",
                                  "metadata": {
                                    "source": "junit"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
                .andExpect(jsonPath("$.description", is("Request body is malformed or contains invalid field values.")))
                .andExpect(jsonPath("$.details[0]", is("eventTimestamp must use UTC timezone offset Z or +00:00.")));
    }

    @Test
    void submitEvent_whenRequiredFieldsAreMissing_returnsValidationErrorWithFieldDetails() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "metadata": {
                                    "source": "junit"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.description", is("Request validation failed.")))
                .andExpect(jsonPath("$.details", hasItem("eventId: must not be null")))
                .andExpect(jsonPath("$.details", hasItem("accountId: must not be blank")))
                .andExpect(jsonPath("$.details", hasItem("type: must not be null")))
                .andExpect(jsonPath("$.details", hasItem("amount: must not be null")))
                .andExpect(jsonPath("$.details", hasItem("currency: must not be blank")))
                .andExpect(jsonPath("$.details", hasItem("eventTimestamp: must not be null")));
    }

    @Test
    void submitEvent_whenAmountIsZero_returnsValidationError() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "d5fc65cb-3949-41b7-9091-1e565f43a06f",
                                  "accountId": "acct-123",
                                  "type": "CREDIT",
                                  "amount": 0.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details", hasItem("amount: must be greater than 0")));
    }

    @Test
    void submitEvent_whenTypeIsUnknown_returnsMalformedRequest() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "2826b837-f49c-43cb-99d9-f23c2adccddb",
                                  "accountId": "acct-123",
                                  "type": "TRANSFER",
                                  "amount": 150.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T14:02:11Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
                .andExpect(jsonPath("$.details[0]", containsString("TRANSFER")));
    }
}
