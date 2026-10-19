create table audit_log (
    id bigserial primary key,
    actor varchar(64) not null,
    action varchar(64) not null,
    entity_type varchar(64) not null,
    entity_id varchar(64) not null,
    at timestamptz not null,
    details jsonb
);

create index idx_audit_log_entity on audit_log (entity_type, entity_id);
create index idx_audit_log_at on audit_log (at);
create index idx_merchants_search on merchants (lower(legal_name), business_email);
