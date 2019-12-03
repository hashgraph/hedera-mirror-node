---
--- Add transaction bytes to t_transactions table
---
alter table t_transactions
    add column if not exists transaction_bytes bytea null;
