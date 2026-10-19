create table merchants
(
    id               uuid         primary key,
    legal_name       varchar(200) not null,
    business_email   varchar(200) not null unique,
    phone            varchar(20),
    status           varchar(16)  not null,
    mdr_bps          int          not null,
    bank_account_ref varchar(64),
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    version          bigint       not null default 0,
    constraint chk_merchants_status check (status in ('PENDING', 'ACTIVE', 'SUSPENDED')),
    constraint chk_merchants_mdr_bps check (mdr_bps between 0 and 2000)
);

create index idx_merchants_status on merchants (status);
