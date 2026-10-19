create table settlement_batches
(
    id                  uuid         primary key,
    business_date       date         not null,
    status              varchar(20)  not null,
    total_merchants     int          not null default 0,
    processed_merchants int          not null default 0,
    failed_merchants    int          not null default 0,
    idempotency_key     varchar(80)  not null unique,
    started_at          timestamptz,
    completed_at        timestamptz,
    created_at          timestamptz  not null,
    version             bigint       not null default 0,
    constraint chk_settlement_batches_status check (status in
        ('CREATED', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED'))
);

create index idx_settlement_batches_business_date on settlement_batches (business_date);

create table settlements
(
    id             uuid         primary key,
    batch_id       uuid         not null references settlement_batches (id),
    merchant_id    uuid         not null references merchants (id),
    gross_minor    bigint,
    mdr_minor      bigint,
    net_minor      bigint,
    txn_count      int,
    currency       char(3),
    status         varchar(16)  not null,
    failure_reason varchar(500),
    created_at     timestamptz  not null,
    constraint uq_settlements_batch_merchant unique (batch_id, merchant_id),
    constraint chk_settlements_status check (status in ('CREATED', 'FAILED'))
);

create index idx_settlements_batch_id on settlements (batch_id);
create index idx_settlements_merchant_id on settlements (merchant_id);

alter table transactions
    add constraint fk_transactions_settlement
        foreign key (settlement_id) references settlements (id);
