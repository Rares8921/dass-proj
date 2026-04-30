alter table audit_logs add column if not exists ticket_id uuid;
alter table audit_logs add column if not exists request_method varchar(16);
alter table audit_logs add column if not exists request_uri varchar(1024);
alter table audit_logs add column if not exists query_string text;
alter table audit_logs add column if not exists request_headers text;
alter table audit_logs add column if not exists request_cookies text;
alter table audit_logs add column if not exists request_parameters text;
alter table audit_logs add column if not exists request_body text;
alter table audit_logs add column if not exists request_content_type varchar(255);
alter table audit_logs add column if not exists response_status integer;
alter table audit_logs add column if not exists response_headers text;
alter table audit_logs add column if not exists response_body text;
alter table audit_logs add column if not exists response_content_type varchar(255);
alter table audit_logs add column if not exists request_flags text;
alter table audit_logs add column if not exists response_flags text;
alter table audit_logs add column if not exists user_agent varchar(1024);
alter table audit_logs add column if not exists referer varchar(1024);
alter table audit_logs add column if not exists authenticated boolean;
alter table audit_logs add column if not exists success boolean;
alter table audit_logs add column if not exists duration_ms bigint;

alter table audit_logs
    add constraint fk_audit_logs_ticket
    foreign key (ticket_id) references tickets(id);

create index if not exists idx_audit_logs_ticket_id on audit_logs(ticket_id);
create index if not exists idx_audit_logs_request_uri on audit_logs(request_uri);
