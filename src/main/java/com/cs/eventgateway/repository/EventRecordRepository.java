package com.cs.eventgateway.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cs.eventgateway.entity.ApplyStatus;
import com.cs.eventgateway.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for gateway ledger events.
 */
public interface EventRecordRepository extends JpaRepository<EventRecord, UUID> {

    /**
     * Finds all ledger events for an account in deterministic business-time order.
     *
     * <p>The ordering uses the upstream event timestamp first so out-of-order
     * delivery does not change the business sequence returned to clients.
     * Creation time and event id are included as stable tie-breakers.</p>
     *
     * @param accountId account whose ledger events should be returned
     * @return matching ledger records ordered by timestamp, creation time, and event id
     */
    List<EventRecord> findByAccountIdOrderByEventTimestampAscCreatedAtAscEventIdAsc(String accountId);

    /**
     * Finds a bounded batch of retryable events whose next apply attempt is due.
     *
     * <p>The scheduler uses this query to avoid unbounded retry scans. Ordering
     * by {@code updatedAt} gives older failed records priority when more than
     * one hundred events are eligible.</p>
     *
     * @param applyStatuses statuses considered retryable by the ledger service
     * @param nextApplyAttemptAt current time cutoff for due retry attempts
     * @return up to one hundred due retry records ordered by oldest update first
     */
    List<EventRecord> findTop100ByApplyStatusInAndNextApplyAttemptAtLessThanEqualOrderByUpdatedAtAsc(
            Collection<ApplyStatus> applyStatuses,
            Instant nextApplyAttemptAt
    );
}
