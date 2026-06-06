create table if not exists event_records (
    event_id varchar(128) not null primary key,
    account_id varchar(128) not null,
    event_type varchar(16) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    event_timestamp timestamp with time zone not null,
    metadata_json clob,
    apply_status varchar(32) not null,
    account_service_error varchar(1024),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_event_records_account_timestamp
    on event_records (account_id, event_timestamp, created_at, event_id);
