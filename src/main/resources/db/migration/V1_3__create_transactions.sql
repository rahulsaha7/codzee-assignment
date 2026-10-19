create table transactions
(
    id            uuid        primary key,
    merchant_id   uuid        not null references merchants (id),
    external_ref  varchar(64) not null,
    amount_minor  bigint      not null,
    currency      char(3)     not null,
    status        varchar(16) not null,
    captured_at   timestamptz not null,
    settlement_id uuid,
    constraint uq_transactions_merchant_external_ref unique (merchant_id, external_ref),
    constraint chk_transactions_amount_minor check (amount_minor > 0),
    constraint chk_transactions_status check (status in ('CAPTURED', 'SETTLED', 'REFUNDED'))
);

create index idx_transactions_merchant_status_captured_at
    on transactions (merchant_id, status, captured_at);

create index idx_transactions_settlement_id on transactions (settlement_id);
