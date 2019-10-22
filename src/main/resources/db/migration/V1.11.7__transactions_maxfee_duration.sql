--
-- Add validDuration and maxFee columns to t_transactions
--

alter table t_transactions
    add column valid_duration bigint null,
        column max_fee bigint null;
