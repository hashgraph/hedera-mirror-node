-- July 20th 2019 - ADDITION OF "SECONDS" column to t_account_balance_history

\echo Adding seconds column to t_account_balance_history
ALTER TABLE t_account_balance_history ADD COLUMN seconds BIGINT;

\echo Populating t_account_balance_history.seconds
UPDATE t_account_balance_history
SET seconds = EXTRACT(EPOCH FROM snapshot_time)
WHERE seconds IS NULL;

\echo Creating index on t_account_balance_history.seconds
CREATE INDEX idx_t_account_bal_hist_sec ON t_account_balance_history (seconds);

\echo Setting t_account_balance_history.seconds to NOT NULL
ALTER TABLE t_account_balance_history ALTER COLUMN seconds SET NOT NULL;
