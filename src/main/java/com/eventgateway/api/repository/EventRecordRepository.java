package com.eventgateway.api.repository;

import java.util.List;

import com.eventgateway.api.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for gateway ledger events.
 */
public interface EventRecordRepository extends JpaRepository<EventRecord, String> {

    List<EventRecord> findByAccountIdOrderByEventTimestampAscCreatedAtAscEventIdAsc(String accountId);
}
