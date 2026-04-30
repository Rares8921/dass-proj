create table if not exists users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(512) not null,
    role varchar(32) not null,
    locked boolean not null,
    failed_login_attempts integer not null,
    locked_until timestamp with time zone,
    session_token varchar(128),
    session_issued_at timestamp with time zone,
    session_expires_at timestamp with time zone,
    reset_password_token varchar(128),
    reset_password_token_expires_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists tickets (
    id uuid primary key,
    title varchar(180) not null,
    description text not null,
    severity varchar(32) not null,
    status varchar(32) not null,
    owner_id uuid not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_tickets_owner foreign key (owner_id) references users(id)
);

create table if not exists audit_logs (
    id uuid primary key,
    user_id uuid,
    action varchar(80) not null,
    resource varchar(80) not null,
    resource_id varchar(64),
    created_at timestamp with time zone not null,
    ip_address varchar(64),
    constraint fk_audit_logs_user foreign key (user_id) references users(id)
);

create index if not exists idx_tickets_owner_id on tickets(owner_id);
create index if not exists idx_audit_logs_user_id on audit_logs(user_id);
create index if not exists idx_audit_logs_created_at on audit_logs(created_at);
