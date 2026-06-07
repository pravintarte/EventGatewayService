create table if not exists event_records (
    event_id uuid not null primary key,
    account_id varchar(128) not null,
    event_type varchar(16) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    event_timestamp timestamp with time zone not null,
    metadata_json clob,
    apply_status varchar(32) not null,
    account_service_error varchar(1024),
    apply_attempt_count integer not null default 0,
    last_apply_attempt_at timestamp with time zone,
    next_apply_attempt_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_event_records_account_timestamp
    on event_records (account_id, event_timestamp, created_at, event_id);

create index if not exists idx_event_records_apply_retry
    on event_records (apply_status, next_apply_attempt_at, updated_at);
