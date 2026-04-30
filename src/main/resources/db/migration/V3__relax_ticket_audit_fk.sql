alter table audit_logs drop constraint if exists fk_audit_logs_ticket;

alter table audit_logs
    add constraint fk_audit_logs_ticket
    foreign key (ticket_id) references tickets(id) on delete set null;
