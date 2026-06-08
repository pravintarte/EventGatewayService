package com.cs.eventgateway.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.cs.eventgateway.client.AccountApplyResult;
import com.cs.eventgateway.client.AccountServiceClient;
import com.cs.eventgateway.repository.EventRecordRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cucumber step definitions for Event Gateway acceptance scenarios.
 */
public class EventGatewayAcceptanceSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRecordRepository eventRecordRepository;

    @Autowired
    private AccountServiceClient accountServiceClient;

    private MvcResult lastResult;
    private JsonNode lastJson;
    private String lastEventId;
    private String lastAccountId;
    private String lastEventTimestamp;
    private String lastEventJson;

    @Before
    public void resetScenarioState() {
        eventRecordRepository.deleteAll();
        reset(accountServiceClient);
        when(accountServiceClient.applyTransaction(any(), any())).thenReturn(AccountApplyResult.success());
        this.lastResult = null;
        this.lastJson = null;
        this.lastEventId = null;
        this.lastAccountId = null;
        this.lastEventTimestamp = null;
        this.lastEventJson = null;
    }

    @Given("Account Service applies transactions successfully")
    public void accountServiceAppliesTransactionsSuccessfully() {
        when(accountServiceClient.applyTransaction(any(), any())).thenReturn(AccountApplyResult.success());
    }

    @Given("Account Service apply fails with {string}")
    public void accountServiceApplyFailsWith(String errorMessage) {
        when(accountServiceClient.applyTransaction(any(), any()))
                .thenReturn(AccountApplyResult.failure(errorMessage));
    }

    @Given("I submitted a valid CREDIT event {string} for account {string} at {string}")
    public void iSubmittedAValidCreditEventForAccountAt(String eventId, String accountId, String eventTimestamp)
            throws Exception {
        iSubmitAValidCreditEventForAccountAt(eventId, accountId, eventTimestamp);
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(201);
    }

    @When("I submit a valid CREDIT event {string} for account {string} at {string}")
    public void iSubmitAValidCreditEventForAccountAt(String eventId, String accountId, String eventTimestamp)
            throws Exception {
        this.lastEventId = eventId;
        this.lastAccountId = accountId;
        this.lastEventTimestamp = eventTimestamp;
        this.lastEventJson = validCreditEvent(eventId, accountId, eventTimestamp, "150.00");
        submitEventJson(this.lastEventJson);
    }

    @When("I submit a valid CREDIT event {string} for account {string} at {string} with trace header {string}")
    public void iSubmitAValidCreditEventForAccountAtWithTraceHeader(
            String eventId,
            String accountId,
            String eventTimestamp,
            String traceHeader
    ) throws Exception {
        this.lastEventId = eventId;
        this.lastAccountId = accountId;
        this.lastEventTimestamp = eventTimestamp;
        this.lastEventJson = validCreditEvent(eventId, accountId, eventTimestamp, "150.00");
        submitEventJson(this.lastEventJson, traceHeader);
    }

    @When("I submit the same event again")
    public void iSubmitTheSameEventAgain() throws Exception {
        assertThat(lastEventJson).isNotNull();
        submitEventJson(lastEventJson);
    }

    @When("I submit the same event id for account {string}")
    public void iSubmitTheSameEventIdForAccount(String accountId) throws Exception {
        assertThat(lastEventId).isNotNull();
        submitEventJson(validCreditEvent(lastEventId, accountId, lastEventTimestamp, "150.00"));
    }

    @When("I submit a CREDIT event {string} with amount {string}")
    public void iSubmitACreditEventWithAmount(String eventId, String amount) throws Exception {
        submitEventJson(validCreditEvent(eventId, "acct-123", "2026-05-15T14:02:11Z", amount));
    }

    @When("I list events for account {string}")
    public void iListEventsForAccount(String accountId) throws Exception {
        this.lastResult = mockMvc.perform(get("/events").param("account", accountId))
                .andReturn();
        this.lastJson = readJson(lastResult);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expectedStatus) {
        assertThat(lastResult).isNotNull();
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expectedStatus);
    }

    @Then("the response code is {string}")
    public void theResponseCodeIs(String expectedCode) {
        assertThat(rootField("code")).isEqualTo(expectedCode);
    }

    @Then("the response data field {string} is {string}")
    public void theResponseDataFieldIs(String fieldName, String expectedValue) {
        assertThat(dataField(fieldName)).isEqualTo(expectedValue);
    }

    @Then("the response error details contain {string}")
    public void theResponseErrorDetailsContain(String expectedDetail) {
        JsonNode details = lastJson.get("details");
        assertThat(details).isNotNull();
        assertThat(details.isArray()).isTrue();

        List<String> actualDetails = new ArrayList<>();
        for (JsonNode detail : details) {
            actualDetails.add(detail.asString());
        }
        assertThat(actualDetails).contains(expectedDetail);
    }

    @Then("Account Service has applied {int} transaction")
    @Then("Account Service has applied {int} transactions")
    public void accountServiceHasAppliedTransactions(int expectedCount) {
        verify(accountServiceClient, times(expectedCount)).applyTransaction(any(), any());
    }

    @Then("the response trace header is a Zipkin trace id")
    public void theResponseTraceHeaderIsAZipkinTraceId() {
        assertThat(responseTraceHeader()).matches("[a-f0-9]{16}|[a-f0-9]{32}");
    }

    @Then("the response trace header is not {string}")
    public void theResponseTraceHeaderIsNot(String unexpectedTraceId) {
        assertThat(responseTraceHeader()).isNotEqualTo(unexpectedTraceId);
    }

    @Then("the events are returned in this order:")
    public void theEventsAreReturnedInThisOrder(DataTable dataTable) {
        List<String> expectedEventIds = dataTable.asList(String.class);
        JsonNode events = lastJson.get("data");
        assertThat(events).isNotNull();
        assertThat(events.isArray()).isTrue();

        List<String> actualEventIds = new ArrayList<>();
        for (JsonNode event : events) {
            actualEventIds.add(event.get("eventId").asString());
        }
        assertThat(actualEventIds).containsExactlyElementsOf(expectedEventIds);
    }

    private void submitEventJson(String eventJson) throws Exception {
        submitEventJson(eventJson, null);
    }

    private void submitEventJson(String eventJson, String traceHeader) throws Exception {
        var request = post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson);
        if (traceHeader != null && !traceHeader.isBlank()) {
            request.header("X-Trace-Id", traceHeader);
        }
        this.lastResult = mockMvc.perform(request).andReturn();
        this.lastJson = readJson(lastResult);
    }

    private String responseTraceHeader() {
        assertThat(lastResult).isNotNull();
        String traceHeader = lastResult.getResponse().getHeader("X-Trace-Id");
        assertThat(traceHeader).isNotBlank();
        return traceHeader;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        String content = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(content).isNotBlank();
        return objectMapper.readTree(content);
    }

    private String rootField(String fieldName) {
        assertThat(lastJson).isNotNull();
        JsonNode field = lastJson.get(fieldName);
        assertThat(field).isNotNull();
        return field.asString();
    }

    private String dataField(String fieldName) {
        assertThat(lastJson).isNotNull();
        JsonNode data = lastJson.get("data");
        assertThat(data).isNotNull();
        JsonNode field = data.get(fieldName);
        assertThat(field).isNotNull();
        return field.asString();
    }

    private String validCreditEvent(String eventId, String accountId, String eventTimestamp, String amount) {
        return """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "CREDIT",
                  "amount": %s,
                  "currency": "USD",
                  "eventTimestamp": "%s",
                  "metadata": {
                    "source": "cucumber-acceptance",
                    "batchId": "B-9042"
                  }
                }
                """.formatted(eventId, accountId, amount, eventTimestamp);
    }
}
