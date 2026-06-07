package com.cs.eventgateway.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cs.eventgateway.client.AccountApplyResult;
import com.cs.eventgateway.client.AccountServiceClient;
import com.cs.eventgateway.dto.event.AccountTransactionRequest;
import com.cs.eventgateway.dto.event.EventResponse;
import com.cs.eventgateway.dto.event.EventSubmissionResponse;
import com.cs.eventgateway.dto.event.EventType;
import com.cs.eventgateway.dto.event.TransactionEventRequest;
import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.entity.EventRecord;
import com.cs.eventgateway.exception.DuplicateEventConflictException;
import com.cs.eventgateway.repository.EventRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Persistence-backed tests for the Event Ledger correctness rules.
 *
 * <p>These tests intentionally use the real JPA repository and H2 schema
 * instead of a mocked repository. Idempotency is partly enforced by the
 * database primary key on {@code event_id}, so repository and database behavior
 * must be included in the test boundary. Account Service is mocked because the
 * gateway behavior under test is whether it calls the dependency exactly once,
 * records success/failure correctly, and avoids duplicate downstream effects.</p>
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "event-gateway.retry.enabled=false"
})
class EventLedgerServiceTest {

    private static final String EVENT_001 = "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2";
    private static final String EVENT_DUPLICATE = "5ecf9f54-2f32-41b0-9b48-e879842f3fb5";
    private static final String EVENT_CONFLICT = "030c7912-a00a-4bd9-9ef0-6ed9b9d7b42c";
    private static final String EVENT_LATE = "0bd17867-d631-44f1-ae8f-a6a726761785";
    private static final String EVENT_EARLY = "991f1f51-27cf-4f90-8ea9-2e867a044581";
    private static final String EVENT_OUTAGE = "ecd6708e-2ad9-469c-8594-90205db53604";
    private static final String EVENT_REJECTED = "4cc7552b-46e3-48af-9686-0ac4cfa43c3c";
    private static final String EVENT_RETRY = "75304861-a3b2-47ff-877a-c47c890a536d";
    private static final String EVENT_CONCURRENT = "6693f1c0-7c29-433b-8425-0c96d1efe4f3";
    private static final String EVENT_DEBIT = "d4e36ed2-5c1c-451d-a2fa-440c6c01210f";

    @Autowired
    private EventRecordRepository eventRecordRepository;

    @Autowired
    private AccountServiceClient accountServiceClient;

    @Autowired
    private EventLedgerService eventLedgerService;

    /**
     * Clears the in-memory ledger and resets the Account Service mock before
     * every test.
     *
     * <p>The default Account Service behavior is success because most tests
     * focus on ledger and idempotency behavior. Tests that need a downstream
     * outage override this default in their own setup section.</p>
     */
    @BeforeEach
    void setUp() {
        eventRecordRepository.deleteAll();
        reset(accountServiceClient);
        when(accountServiceClient.applyTransaction(any(), any())).thenReturn(AccountApplyResult.success());
    }

    /**
     * Verifies the normal happy path for a first-time event submission.
     *
     * <p>The service should store the event, call Account Service once, mark the
     * event as {@link ApplyStatus#APPLIED}, and return a non-duplicate response.
     * This confirms that a new event id follows the create-and-apply path rather
     * than being treated as an idempotent retry.</p>
     */
    @Test
    void submit_whenEventIdHasNeverBeenSeen_persistsEventCallsAccountServiceAndReturnsAppliedResponse() {
        TransactionEventRequest request = event(EVENT_001, "acct-123", "2026-05-15T14:02:11Z");

        EventSubmissionResponse response = eventLedgerService.submit(request);

        assertThat(response.eventId()).isEqualTo(UUID.fromString(EVENT_001));
        assertThat(response.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(response.duplicate()).isFalse();
        assertThat(response.message()).isEqualTo("EVENT_ACCEPTED_AND_APPLIED");
        verify(accountServiceClient).applyTransaction(eq("acct-123"), any());
    }

    @Test
    void submit_whenDebitEventIsReceived_persistsAndForwardsDebitToAccountService() {
        TransactionEventRequest request = event(
                EVENT_DEBIT,
                "acct-123",
                EventType.DEBIT,
                new BigDecimal("40.25"),
                "2026-05-15T14:02:11Z"
        );

        EventSubmissionResponse response = eventLedgerService.submit(request);

        ArgumentCaptor<AccountTransactionRequest> transactionCaptor =
                ArgumentCaptor.forClass(AccountTransactionRequest.class);
        assertThat(response.type()).isEqualTo(EventType.DEBIT);
        assertThat(response.amount()).isEqualByComparingTo("40.25");
        verify(accountServiceClient).applyTransaction(eq("acct-123"), transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().type()).isEqualTo(EventType.DEBIT);
        assertThat(transactionCaptor.getValue().amount()).isEqualByComparingTo("40.25");
    }

    /**
     * Verifies sequential idempotency for two identical submissions.
     *
     * <p>The first request should be processed normally. The second request has
     * the same {@code eventId} and identical business payload, so it should
     * return the existing ledger record with {@code duplicate=true}. The test
     * also verifies that Account Service is called only once, which is the main
     * side-effect protection required for idempotency.</p>
     */
    @Test
    void submit_whenExactDuplicateArrivesSequentially_returnsExistingRecordWithoutCallingAccountServiceAgain() {
        TransactionEventRequest request = event(EVENT_DUPLICATE, "acct-123", "2026-05-15T14:02:11Z");

        EventSubmissionResponse first = eventLedgerService.submit(request);
        EventSubmissionResponse duplicate = eventLedgerService.submit(request);

        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.status()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(duplicate.message()).isEqualTo("DUPLICATE_EVENT_IGNORED");
        verify(accountServiceClient, times(1)).applyTransaction(eq("acct-123"), any());
    }

    /**
     * Verifies that an event id cannot be reused for a different event body.
     *
     * <p>The first request creates a valid ledger record. The second request
     * reuses the same {@code eventId} but changes the account id. That is not a
     * safe duplicate; it is a conflicting payload. The service should reject it
     * with {@link DuplicateEventConflictException} and must not call Account
     * Service for the conflicting request.</p>
     */
    @Test
    void submit_whenSameEventIdIsReusedWithDifferentPayload_rejectsConflictAndDoesNotReapply() {
        eventLedgerService.submit(event(EVENT_CONFLICT, "acct-123", "2026-05-15T14:02:11Z"));

        TransactionEventRequest conflicting = new TransactionEventRequest(
                UUID.fromString(EVENT_CONFLICT),
                "acct-999",
                EventType.CREDIT,
                new BigDecimal("150.00"),
                "USD",
                Instant.parse("2026-05-15T14:02:11Z"),
                Map.of("source", "mainframe-batch")
        );

        assertThatThrownBy(() -> eventLedgerService.submit(conflicting))
                .isInstanceOf(DuplicateEventConflictException.class);
        verify(accountServiceClient, times(1)).applyTransaction(eq("acct-123"), any());
    }

    /**
     * Verifies that account event listing uses business event time instead of
     * gateway arrival order.
     *
     * <p>The later event is submitted first and the earlier event is submitted
     * second. The returned list must still place the earlier event first because
     * callers use this endpoint to inspect the ledger in the order events
     * originally occurred.</p>
     */
    @Test
    void listEventsForAccount_whenEventsArriveOutOfOrder_returnsEventsSortedByOriginalEventTimestamp() {
        eventLedgerService.submit(event(EVENT_LATE, "acct-123", "2026-05-15T15:00:00Z"));
        eventLedgerService.submit(event(EVENT_EARLY, "acct-123", "2026-05-15T14:00:00Z"));

        List<EventResponse> events = eventLedgerService.listEventsForAccount("acct-123");

        assertThat(events).extracting(EventResponse::eventId)
                .containsExactly(UUID.fromString(EVENT_EARLY), UUID.fromString(EVENT_LATE));
    }

    /**
     * Verifies graceful behavior when Account Service cannot apply the event.
     *
     * <p>The gateway should not lose the inbound event simply because a
     * downstream service is unavailable. Instead, it should keep the ledger
     * record, mark it as {@link ApplyStatus#APPLY_FAILED}, store the downstream
     * error message, and allow the event to be retrieved later.</p>
     */
    @Test
    void submit_whenAccountServiceApplyFails_keepsLedgerEventAndMarksApplyFailedForLaterInspection() {
        when(accountServiceClient.applyTransaction(any(), any()))
                .thenReturn(AccountApplyResult.failure("Connection refused"));

        EventSubmissionResponse response = eventLedgerService.submit(
                event(EVENT_OUTAGE, "acct-123", "2026-05-15T14:02:11Z")
        );

        assertThat(response.status()).isEqualTo(ApplyStatus.APPLY_FAILED);
        assertThat(response.message()).isEqualTo("EVENT_ACCEPTED_ACCOUNT_SERVICE_UNAVAILABLE");
        assertThat(response.accountServiceError()).isEqualTo("Connection refused");
        assertThat(eventLedgerService.getEvent(UUID.fromString(EVENT_OUTAGE)).status()).isEqualTo(ApplyStatus.APPLY_FAILED);
    }

    @Test
    void submit_whenAccountServiceRejectsEvent_marksLedgerEventRejectedAndDoesNotScheduleRetry() {
        when(accountServiceClient.applyTransaction(any(), any()))
                .thenReturn(AccountApplyResult.rejected("Account is closed"));

        EventSubmissionResponse response = eventLedgerService.submit(
                event(EVENT_REJECTED, "acct-123", "2026-05-15T14:02:11Z")
        );

        EventRecord eventRecord = eventRecordRepository.findById(UUID.fromString(EVENT_REJECTED)).orElseThrow();
        assertThat(response.status()).isEqualTo(ApplyStatus.APPLY_REJECTED);
        assertThat(response.message()).isEqualTo("EVENT_ACCEPTED_ACCOUNT_APPLY_REJECTED");
        assertThat(eventRecord.getAccountServiceError()).isEqualTo("Account is closed");
        assertThat(eventRecord.getApplyAttemptCount()).isEqualTo(1);
        assertThat(eventRecord.getNextApplyAttemptAt()).isNull();
    }

    @Test
    void retryDueEvents_whenFailedEventIsDue_reappliesAndMarksApplied() {
        when(accountServiceClient.applyTransaction(any(), any()))
                .thenReturn(AccountApplyResult.failure("Connection refused"));
        eventLedgerService.submit(event(EVENT_RETRY, "acct-123", "2026-05-15T14:02:11Z"));

        EventRecord failed = eventRecordRepository.findById(UUID.fromString(EVENT_RETRY)).orElseThrow();
        failed.setNextApplyAttemptAt(Instant.parse("2026-01-01T00:00:00Z"));
        eventRecordRepository.save(failed);

        reset(accountServiceClient);
        when(accountServiceClient.applyTransaction(any(), any())).thenReturn(AccountApplyResult.success());

        int attempted = eventLedgerService.retryDueEvents();

        EventRecord retried = eventRecordRepository.findById(UUID.fromString(EVENT_RETRY)).orElseThrow();
        assertThat(attempted).isEqualTo(1);
        assertThat(retried.getApplyStatus()).isEqualTo(ApplyStatus.APPLIED);
        assertThat(retried.getApplyAttemptCount()).isEqualTo(2);
        assertThat(retried.getAccountServiceError()).isNull();
        assertThat(retried.getNextApplyAttemptAt()).isNull();
    }

    /**
     * Verifies idempotency under concurrent duplicate submissions.
     *
     * <p>This test starts multiple threads at the same time with the exact same
     * request body. One thread should win the insert and apply the transaction.
     * The remaining threads should either observe the existing row before
     * insertion or lose the database primary-key race and then reload the
     * winning row. In all cases, Account Service must be called exactly once and
     * only one ledger record should exist for the event id.</p>
     *
     * @throws Exception if worker threads fail, timeout, or interruption occurs
     */
    @Test
    void submit_whenExactDuplicateEventsArriveConcurrently_persistsOneLedgerRecordAndCallsAccountServiceOnce()
            throws Exception {
        int threadCount = 10;
        TransactionEventRequest request = event(EVENT_CONCURRENT, "acct-123", "2026-05-15T14:02:11Z");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<EventSubmissionResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                return eventLedgerService.submit(request);
            });
        }

        List<Future<EventSubmissionResponse>> futures = tasks.stream()
                .map(executorService::submit)
                .toList();
        startGate.countDown();

        List<EventSubmissionResponse> responses = new ArrayList<>();
        for (Future<EventSubmissionResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }

        executorService.shutdown();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(responses).hasSize(threadCount);
        assertThat(responses).extracting(EventSubmissionResponse::eventId)
                .containsOnly(UUID.fromString(EVENT_CONCURRENT));
        assertThat(responses.stream().filter(EventSubmissionResponse::duplicate).count())
                .isEqualTo(threadCount - 1L);
        assertThat(responses.stream().filter(response -> !response.duplicate()).count())
                .isEqualTo(1L);
        assertThat(eventRecordRepository.findById(UUID.fromString(EVENT_CONCURRENT))).isPresent();
        verify(accountServiceClient, times(1)).applyTransaction(eq("acct-123"), any());
    }

    /**
     * Creates a valid credit event request used by the test scenarios.
     *
     * <p>The helper keeps tests focused on the field that matters to each
     * scenario, typically event id, account id, or timestamp. Metadata is
     * included by default so duplicate comparison also covers metadata
     * serialization and equality.</p>
     *
     * @param eventId upstream event id to assign
     * @param accountId account id to assign
     * @param timestamp original event timestamp to assign
     * @return valid transaction event request
     */
    private TransactionEventRequest event(String eventId, String accountId, String timestamp) {
        return event(eventId, accountId, EventType.CREDIT, new BigDecimal("150.00"), timestamp);
    }

    private TransactionEventRequest event(
            String eventId,
            String accountId,
            EventType type,
            BigDecimal amount,
            String timestamp
    ) {
        return new TransactionEventRequest(
                UUID.fromString(eventId),
                accountId,
                type,
                amount,
                "USD",
                Instant.parse(timestamp),
                Map.of("source", "mainframe-batch", "batchId", "B-9042")
        );
    }

    /**
     * Test configuration that replaces the real Account Service REST client
     * with a Mockito mock.
     *
     * <p>The service discovery and HTTP layers are tested separately. For the
     * ledger service, this mock lets tests assert downstream side effects
     * precisely, especially the guarantee that duplicate deliveries do not cause
     * duplicate Account Service transaction applications.</p>
     */
    @TestConfiguration
    static class MockAccountServiceConfig {

        /**
         * Supplies the primary Account Service client bean used by the Spring
         * test context.
         *
         * @return Mockito mock replacing the production REST client
         */
        @Bean
        @Primary
        AccountServiceClient accountServiceClient() {
            return mock(AccountServiceClient.class);
        }
    }
}
