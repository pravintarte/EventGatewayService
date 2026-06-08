Feature: Event Gateway acceptance behavior

  The Event Gateway must accept financial ledger events through its public API,
  protect Account Service from duplicate side effects, expose events in business
  time order, and return stable errors for invalid requests.

  Background:
    Given Account Service applies transactions successfully

  Scenario: Submit a valid event
    When I submit a valid CREDIT event "9b63f0d4-0f49-4f85-9447-fd45a0c5b3c2" for account "acct-123" at "2026-05-15T14:02:11Z"
    Then the response status is 201
    And the response code is "EVENT_CREATED"
    And the response data field "status" is "APPLIED"
    And Account Service has applied 1 transaction

  Scenario: Return the exported trace id in the gateway trace header
    When I submit a valid CREDIT event "8aac4284-2db6-478d-9796-e5a06074d7ef" for account "acct-123" at "2026-05-15T14:02:11Z" with trace header "external-trace"
    Then the response status is 201
    And the response trace header is a Zipkin trace id
    And the response trace header is not "external-trace"
    And Account Service has applied 1 transaction

  Scenario: Ignore an exact duplicate event
    Given I submitted a valid CREDIT event "5ecf9f54-2f32-41b0-9b48-e879842f3fb5" for account "acct-123" at "2026-05-15T14:02:11Z"
    When I submit the same event again
    Then the response status is 200
    And the response code is "EVENT_DUPLICATE"
    And the response data field "duplicate" is "true"
    And Account Service has applied 1 transaction

  Scenario: Reject a duplicate event id with a different payload
    Given I submitted a valid CREDIT event "030c7912-a00a-4bd9-9ef0-6ed9b9d7b42c" for account "acct-123" at "2026-05-15T14:02:11Z"
    When I submit the same event id for account "acct-999"
    Then the response status is 409
    And the response code is "DUPLICATE_EVENT_CONFLICT"
    And Account Service has applied 1 transaction

  Scenario: Return validation error for an invalid event
    When I submit a CREDIT event "31515db2-c81d-47c2-9996-4dbf02a8e75a" with amount "-25.00"
    Then the response status is 400
    And the response code is "VALIDATION_ERROR"
    And Account Service has applied 0 transactions

  Scenario: Reject event timestamp that is not UTC
    When I submit a valid CREDIT event "7fe118d2-1958-4238-8647-a52c55b946c8" for account "acct-123" at "2026-05-15T09:02:11-05:00"
    Then the response status is 400
    And the response code is "MALFORMED_REQUEST"
    And the response error details contain "eventTimestamp must use UTC timezone offset Z or +00:00."
    And Account Service has applied 0 transactions

  Scenario: Keep events ordered by original event timestamp when they arrive out of order
    Given I submitted a valid CREDIT event "0bd17867-d631-44f1-ae8f-a6a726761785" for account "acct-ordered" at "2026-05-15T15:00:00Z"
    And I submitted a valid CREDIT event "991f1f51-27cf-4f90-8ea9-2e867a044581" for account "acct-ordered" at "2026-05-15T14:00:00Z"
    When I list events for account "acct-ordered"
    Then the response status is 200
    And the response code is "EVENTS_LISTED"
    And the events are returned in this order:
      | 991f1f51-27cf-4f90-8ea9-2e867a044581 |
      | 0bd17867-d631-44f1-ae8f-a6a726761785 |

  Scenario: Accept and persist an event when Account Service is unavailable
    Given Account Service apply fails with "Connection refused"
    When I submit a valid CREDIT event "ecd6708e-2ad9-469c-8594-90205db53604" for account "acct-123" at "2026-05-15T14:02:11Z"
    Then the response status is 202
    And the response code is "EVENT_ACCEPTED_ACCOUNT_APPLY_FAILED"
    And the response data field "status" is "APPLY_FAILED"
