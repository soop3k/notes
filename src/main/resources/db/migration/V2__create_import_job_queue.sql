create table import_job (
  id uuid primary key,
  request_id varchar(255) not null unique,
  import_name varchar(100) not null,
  source_file varchar(1000) not null,
  schema_entry varchar(255) not null,
  csv_entry varchar(255) not null,
  delimiter_char char(1) not null,
  import_batch_size integer not null check (import_batch_size > 0),
  record_type varchar(50) not null,
  header_mapping clob not null,
  status varchar(30) not null check (status in ('WAITING_FOR_FILE', 'PROCESSING', 'COMPLETED', 'FAILED')),
  submitted_at timestamp with time zone not null,
  next_poll_at timestamp with time zone not null,
  processing_started_at timestamp with time zone,
  execution_id uuid references import_execution(id),
  completed_at timestamp with time zone,
  error_message varchar(2000)
);

create index idx_import_job_due on import_job(status, next_poll_at);
