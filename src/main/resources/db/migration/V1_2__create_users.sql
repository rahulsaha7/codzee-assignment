create table users
(
    id            uuid        primary key,
    username      varchar(64) not null unique,
    password_hash varchar(100) not null,
    role          varchar(16) not null,
    merchant_id   uuid references merchants (id),
    created_at    timestamptz not null,
    constraint chk_users_role check (role in ('ADMIN', 'OPS', 'MERCHANT'))
);

create index idx_users_merchant_id on users (merchant_id);
