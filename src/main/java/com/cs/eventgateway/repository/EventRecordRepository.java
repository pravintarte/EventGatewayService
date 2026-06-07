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

    List<EventRecord> findByAccountIdOrderByEventTimestampAscCreatedAtAscEventIdAsc(String accountId);

    List<EventRecord> findTop100ByApplyStatusInAndNextApplyAttemptAtLessThanEqualOrderByUpdatedAtAsc(
            Collection<ApplyStatus> applyStatuses,
            Instant nextApplyAttemptAt
    );
}
